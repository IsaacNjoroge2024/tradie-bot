from unittest.mock import MagicMock

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.mt5_client import MT5Client
from app.services.order_service import OrderService
from app.services.position_service import PositionService

client = TestClient(app)


def _setup_app_state(connected: bool = True):
    mock_client = MagicMock(spec=MT5Client)
    mock_client.is_connected.return_value = connected
    app.state.mt5_client = mock_client
    app.state.order_service = MagicMock(spec=OrderService)
    app.state.position_service = MagicMock(spec=PositionService)


@pytest.fixture(autouse=True)
def restore_app_state():
    prev_client = getattr(app.state, "mt5_client", None)
    prev_order = getattr(app.state, "order_service", None)
    prev_position = getattr(app.state, "position_service", None)
    yield
    app.state.mt5_client = prev_client
    app.state.order_service = prev_order
    app.state.position_service = prev_position


class TestHealth:
    def test_health_connected(self):
        _setup_app_state(connected=True)
        response = client.get("/health")
        assert response.status_code == 200
        body = response.json()
        assert body["status"] == "healthy"
        assert body["service"] == "tradie-mt5-bridge"
        assert body["mt5_connected"] is True

    def test_health_disconnected(self):
        _setup_app_state(connected=False)
        response = client.get("/health")
        assert response.status_code == 503
        body = response.json()
        assert body["status"] == "degraded"
        assert body["mt5_connected"] is False


class TestOrderEndpoints:
    def test_place_market_buy_order(self):
        _setup_app_state()
        from app.mt5_client import OrderResult

        mock_result = OrderResult(
            success=True, order_id=100001, deal_id=200001, volume=0.1, price=1.09123
        )
        app.state.order_service.place_order.return_value = mock_result

        payload = {"symbol": "EURUSD", "side": "BUY", "volume": 0.1, "sl": 1.085, "tp": 1.100}
        response = client.post("/api/orders/place", json=payload)
        assert response.status_code == 200
        body = response.json()
        assert body["success"] is True
        assert body["order_id"] == 100001
        assert body["deal_id"] == 200001
        assert body["volume"] == 0.1
        assert body["price"] == 1.09123

    def test_place_order_failure(self):
        _setup_app_state()
        from app.mt5_client import OrderResult

        app.state.order_service.place_order.return_value = OrderResult(
            success=False, error_code=10018, error_message="Not enough money"
        )

        payload = {"symbol": "EURUSD", "side": "BUY", "volume": 100.0}
        response = client.post("/api/orders/place", json=payload)
        assert response.status_code == 400
        assert "Not enough money" in response.json()["detail"]

    def test_place_limit_order(self):
        _setup_app_state()
        from app.mt5_client import OrderResult

        app.state.order_service.place_order.return_value = OrderResult(
            success=True, order_id=100002, volume=0.05, price=1.085
        )

        payload = {
            "symbol": "EURUSD",
            "side": "BUY",
            "volume": 0.05,
            "order_type": "LIMIT",
            "price": 1.085,
            "sl": 1.080,
            "tp": 1.100,
        }
        response = client.post("/api/orders/place", json=payload)
        assert response.status_code == 200
        assert response.json()["success"] is True

    def test_cancel_order(self):
        _setup_app_state()
        from app.mt5_client import OrderResult

        app.state.order_service.cancel_order.return_value = OrderResult(
            success=True, order_id=100002
        )

        response = client.post("/api/orders/cancel", json={"ticket": 100002})
        assert response.status_code == 200
        body = response.json()
        assert body["success"] is True
        assert body["ticket"] == 100002

    def test_cancel_order_not_found(self):
        _setup_app_state()
        from app.mt5_client import OrderResult

        app.state.order_service.cancel_order.return_value = OrderResult(
            success=False, error_message="Order 999 not found"
        )

        response = client.post("/api/orders/cancel", json={"ticket": 999})
        assert response.status_code == 400

    def test_place_order_missing_required_fields(self):
        _setup_app_state()
        response = client.post("/api/orders/place", json={"symbol": "EURUSD"})
        assert response.status_code == 422

    def test_place_order_connection_error_returns_503(self):
        _setup_app_state()
        app.state.order_service.place_order.side_effect = ConnectionError("MT5 disconnected")
        response = client.post(
            "/api/orders/place", json={"symbol": "EURUSD", "side": "BUY", "volume": 0.1}
        )
        assert response.status_code == 503

    def test_cancel_order_connection_error_returns_503(self):
        _setup_app_state()
        app.state.order_service.cancel_order.side_effect = ConnectionError("MT5 disconnected")
        response = client.post("/api/orders/cancel", json={"ticket": 100001})
        assert response.status_code == 503


class TestPositionEndpoints:
    def test_get_positions_empty(self):
        _setup_app_state()
        app.state.position_service.get_positions.return_value = []
        response = client.get("/api/positions/")
        assert response.status_code == 200
        assert response.json() == []

    def test_get_positions_with_data(self):
        _setup_app_state()
        app.state.position_service.get_positions.return_value = [
            {
                "ticket": 300001,
                "symbol": "EURUSD",
                "type": "BUY",
                "volume": 0.1,
                "open_price": 1.09000,
                "current_price": 1.09200,
                "sl": 1.085,
                "tp": 1.100,
                "profit": 20.0,
                "swap": -0.5,
                "magic": 123456,
                "comment": "Tradie",
                "open_time": 1700000000,
            }
        ]
        response = client.get("/api/positions/")
        assert response.status_code == 200
        body = response.json()
        assert len(body) == 1
        assert body[0]["symbol"] == "EURUSD"
        assert body[0]["type"] == "BUY"

    def test_get_positions_by_symbol(self):
        _setup_app_state()
        app.state.position_service.get_positions.return_value = []
        response = client.get("/api/positions/?symbol=EURUSD")
        assert response.status_code == 200
        app.state.position_service.get_positions.assert_called_once_with(symbol="EURUSD")

    def test_get_pending_orders(self):
        _setup_app_state()
        app.state.position_service.get_pending_orders.return_value = [
            {
                "ticket": 400001,
                "symbol": "GBPUSD",
                "type": "BUY_LIMIT",
                "volume": 0.1,
                "price": 1.265,
                "sl": 1.260,
                "tp": 1.280,
                "magic": 123456,
                "comment": "Tradie",
            }
        ]
        response = client.get("/api/positions/pending")
        assert response.status_code == 200
        body = response.json()
        assert len(body) == 1
        assert body[0]["type"] == "BUY_LIMIT"

    def test_modify_position(self):
        _setup_app_state()
        from app.mt5_client import OrderResult

        app.state.position_service.modify_position.return_value = OrderResult(
            success=True, order_id=300001
        )

        payload = {"ticket": 300001, "sl": 1.088, "tp": 1.105}
        response = client.post("/api/positions/modify", json=payload)
        assert response.status_code == 200
        assert response.json()["success"] is True
        assert response.json()["ticket"] == 300001

    def test_modify_position_not_found(self):
        _setup_app_state()
        from app.mt5_client import OrderResult

        app.state.position_service.modify_position.return_value = OrderResult(
            success=False, error_message="Position 999 not found"
        )

        response = client.post("/api/positions/modify", json={"ticket": 999, "sl": 1.085})
        assert response.status_code == 400

    def test_close_position(self):
        _setup_app_state()
        from app.mt5_client import OrderResult

        app.state.position_service.close_position.return_value = OrderResult(
            success=True, deal_id=500001, volume=0.1, price=1.09200
        )

        response = client.post("/api/positions/close", json={"ticket": 300001})
        assert response.status_code == 200
        body = response.json()
        assert body["success"] is True
        assert body["deal_id"] == 500001
        assert body["volume"] == 0.1

    def test_close_position_partial(self):
        _setup_app_state()
        from app.mt5_client import OrderResult

        app.state.position_service.close_position.return_value = OrderResult(
            success=True, deal_id=500002, volume=0.05, price=1.09200
        )

        response = client.post("/api/positions/close", json={"ticket": 300001, "volume": 0.05})
        assert response.status_code == 200
        assert response.json()["volume"] == 0.05

    def test_close_position_failure(self):
        _setup_app_state()
        from app.mt5_client import OrderResult

        app.state.position_service.close_position.return_value = OrderResult(
            success=False, error_message="Position 999 not found"
        )

        response = client.post("/api/positions/close", json={"ticket": 999})
        assert response.status_code == 400

    def test_close_all_positions(self):
        _setup_app_state()
        from app.mt5_client import OrderResult

        app.state.position_service.close_all_positions.return_value = [
            OrderResult(success=True, deal_id=500001, volume=0.1, price=1.09200),
            OrderResult(success=True, deal_id=500002, volume=0.2, price=1.265),
        ]

        response = client.post("/api/positions/close-all")
        assert response.status_code == 200
        body = response.json()
        assert body["closed"] == 2
        assert body["failed"] == 0

    def test_close_all_positions_by_symbol(self):
        _setup_app_state()
        from app.mt5_client import OrderResult

        app.state.position_service.close_all_positions.return_value = [
            OrderResult(success=True, deal_id=500001, volume=0.1, price=1.09200),
        ]

        response = client.post("/api/positions/close-all?symbol=EURUSD")
        assert response.status_code == 200
        app.state.position_service.close_all_positions.assert_called_once_with(symbol="EURUSD")

    def test_close_all_partial_failure(self):
        _setup_app_state()
        from app.mt5_client import OrderResult

        app.state.position_service.close_all_positions.return_value = [
            OrderResult(success=True, deal_id=500001, volume=0.1, price=1.092),
            OrderResult(success=False, error_message="Trade not allowed"),
        ]

        response = client.post("/api/positions/close-all")
        assert response.status_code == 200
        body = response.json()
        assert body["closed"] == 1
        assert body["failed"] == 1

    def test_get_positions_connection_error(self):
        _setup_app_state()
        app.state.position_service.get_positions.side_effect = ConnectionError("MT5 disconnected")
        response = client.get("/api/positions/")
        assert response.status_code == 503

    def test_modify_position_connection_error_returns_503(self):
        _setup_app_state()
        app.state.position_service.modify_position.side_effect = ConnectionError("MT5 disconnected")
        response = client.post("/api/positions/modify", json={"ticket": 300001, "sl": 1.085})
        assert response.status_code == 503

    def test_close_position_connection_error_returns_503(self):
        _setup_app_state()
        app.state.position_service.close_position.side_effect = ConnectionError("MT5 disconnected")
        response = client.post("/api/positions/close", json={"ticket": 300001})
        assert response.status_code == 503


class TestAccountEndpoints:
    def test_get_account_info(self):
        _setup_app_state()
        app.state.mt5_client.get_account_info.return_value = {
            "login": 12345678,
            "server": "Exness-MT5Demo2",
            "balance": 10000.0,
            "equity": 10050.0,
            "margin": 100.0,
            "free_margin": 9950.0,
            "margin_level": 10050.0,
            "leverage": 500,
            "currency": "USD",
            "profit": 50.0,
        }

        response = client.get("/api/account/info")
        assert response.status_code == 200
        body = response.json()
        assert body["login"] == 12345678
        assert body["server"] == "Exness-MT5Demo2"
        assert body["balance"] == 10000.0
        assert body["leverage"] == 500

    def test_get_account_info_connection_error(self):
        _setup_app_state()
        app.state.mt5_client.get_account_info.side_effect = ConnectionError("MT5 disconnected")
        response = client.get("/api/account/info")
        assert response.status_code == 503

    def test_get_account_info_empty(self):
        _setup_app_state()
        app.state.mt5_client.get_account_info.return_value = {}
        response = client.get("/api/account/info")
        assert response.status_code == 503

    def test_get_symbol_info(self):
        _setup_app_state()
        app.state.mt5_client.get_symbol_info.return_value = {
            "symbol": "EURUSD",
            "bid": 1.09120,
            "ask": 1.09122,
            "spread": 2,
            "digits": 5,
            "point": 0.00001,
            "min_lot": 0.01,
            "max_lot": 200.0,
            "lot_step": 0.01,
            "contract_size": 100000.0,
            "swap_long": -3.5,
            "swap_short": 1.2,
        }

        response = client.get("/api/account/symbol/EURUSD")
        assert response.status_code == 200
        body = response.json()
        assert body["symbol"] == "EURUSD"
        assert body["spread"] == 2
        assert body["min_lot"] == 0.01

    def test_get_symbol_info_not_found(self):
        _setup_app_state()
        app.state.mt5_client.get_symbol_info.return_value = None
        response = client.get("/api/account/symbol/INVALID")
        assert response.status_code == 404

    def test_get_symbol_info_connection_error(self):
        _setup_app_state()
        app.state.mt5_client.get_symbol_info.side_effect = ConnectionError("MT5 disconnected")
        response = client.get("/api/account/symbol/EURUSD")
        assert response.status_code == 503
