from unittest.mock import MagicMock

import pytest

from app.mt5_client import MT5Client, OrderResult
from app.services.position_service import PositionService


@pytest.fixture
def mock_client():
    return MagicMock(spec=MT5Client)


@pytest.fixture
def service(mock_client):
    return PositionService(mock_client)


_SAMPLE_POSITION = {
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


class TestGetPositions:
    def test_returns_list_from_client(self, service, mock_client):
        mock_client.get_positions.return_value = [_SAMPLE_POSITION]
        positions = service.get_positions()
        assert len(positions) == 1
        assert positions[0]["symbol"] == "EURUSD"
        mock_client.get_positions.assert_called_once_with(symbol=None)

    def test_filters_by_symbol(self, service, mock_client):
        mock_client.get_positions.return_value = [_SAMPLE_POSITION]
        positions = service.get_positions(symbol="EURUSD")
        mock_client.get_positions.assert_called_once_with(symbol="EURUSD")
        assert len(positions) == 1

    def test_returns_empty_list(self, service, mock_client):
        mock_client.get_positions.return_value = []
        positions = service.get_positions()
        assert positions == []

    def test_propagates_connection_error(self, service, mock_client):
        mock_client.get_positions.side_effect = ConnectionError("MT5 disconnected")
        with pytest.raises(ConnectionError):
            service.get_positions()


class TestGetPendingOrders:
    def test_returns_pending_orders(self, service, mock_client):
        mock_client.get_pending_orders.return_value = [
            {"ticket": 400001, "symbol": "GBPUSD", "type": "BUY_LIMIT", "volume": 0.1}
        ]
        orders = service.get_pending_orders()
        assert len(orders) == 1
        assert orders[0]["type"] == "BUY_LIMIT"

    def test_propagates_connection_error(self, service, mock_client):
        mock_client.get_pending_orders.side_effect = ConnectionError("MT5 disconnected")
        with pytest.raises(ConnectionError):
            service.get_pending_orders()


class TestModifyPosition:
    def test_modify_sl_tp_success(self, service, mock_client):
        mock_client.modify_position.return_value = OrderResult(success=True, order_id=300001)
        result = service.modify_position(300001, sl=1.088, tp=1.105)
        assert result.success is True
        mock_client.modify_position.assert_called_once_with(ticket=300001, sl=1.088, tp=1.105)

    def test_modify_only_sl(self, service, mock_client):
        mock_client.modify_position.return_value = OrderResult(success=True, order_id=300001)
        result = service.modify_position(300001, sl=1.088)
        assert result.success is True
        mock_client.modify_position.assert_called_once_with(ticket=300001, sl=1.088, tp=None)

    def test_modify_failure_returned(self, service, mock_client):
        mock_client.modify_position.return_value = OrderResult(
            success=False, error_message="Position 999 not found"
        )
        result = service.modify_position(999, sl=1.085)
        assert result.success is False
        assert "not found" in result.error_message

    def test_connection_error_returns_failure(self, service, mock_client):
        mock_client.modify_position.side_effect = ConnectionError("MT5 disconnected")
        result = service.modify_position(300001, sl=1.088)
        assert result.success is False
        assert "MT5 disconnected" in result.error_message


class TestClosePosition:
    def test_close_full_position(self, service, mock_client):
        mock_client.close_position.return_value = OrderResult(
            success=True, deal_id=500001, volume=0.1, price=1.09200
        )
        result = service.close_position(300001)
        assert result.success is True
        assert result.deal_id == 500001
        mock_client.close_position.assert_called_once_with(ticket=300001, volume=None)

    def test_close_partial_position(self, service, mock_client):
        mock_client.close_position.return_value = OrderResult(
            success=True, deal_id=500002, volume=0.05, price=1.09200
        )
        result = service.close_position(300001, volume=0.05)
        assert result.success is True
        mock_client.close_position.assert_called_once_with(ticket=300001, volume=0.05)

    def test_close_position_failure_returned(self, service, mock_client):
        mock_client.close_position.return_value = OrderResult(
            success=False, error_message="Position 999 not found"
        )
        result = service.close_position(999)
        assert result.success is False

    def test_connection_error_returns_failure(self, service, mock_client):
        mock_client.close_position.side_effect = ConnectionError("MT5 disconnected")
        result = service.close_position(300001)
        assert result.success is False
        assert "MT5 disconnected" in result.error_message


class TestCloseAllPositions:
    def test_close_all_success(self, service, mock_client):
        mock_client.close_all_positions.return_value = [
            OrderResult(success=True, deal_id=500001, volume=0.1, price=1.09200),
            OrderResult(success=True, deal_id=500002, volume=0.2, price=1.265),
        ]
        results = service.close_all_positions()
        assert len(results) == 2
        assert all(r.success for r in results)
        mock_client.close_all_positions.assert_called_once_with(symbol=None)

    def test_close_all_by_symbol(self, service, mock_client):
        mock_client.close_all_positions.return_value = [
            OrderResult(success=True, deal_id=500001, volume=0.1, price=1.09200),
        ]
        results = service.close_all_positions(symbol="EURUSD")
        mock_client.close_all_positions.assert_called_once_with(symbol="EURUSD")
        assert len(results) == 1

    def test_close_all_returns_empty_when_no_positions(self, service, mock_client):
        mock_client.close_all_positions.return_value = []
        results = service.close_all_positions()
        assert results == []

    def test_propagates_connection_error(self, service, mock_client):
        mock_client.close_all_positions.side_effect = ConnectionError("MT5 disconnected")
        with pytest.raises(ConnectionError):
            service.close_all_positions()

    def test_partial_close_failure_reported(self, service, mock_client):
        mock_client.close_all_positions.return_value = [
            OrderResult(success=True, deal_id=500001, volume=0.1, price=1.092),
            OrderResult(success=False, error_message="Trade not allowed"),
        ]
        results = service.close_all_positions()
        assert len(results) == 2
        closed = sum(1 for r in results if r.success)
        failed = len(results) - closed
        assert closed == 1
        assert failed == 1
