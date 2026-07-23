from types import SimpleNamespace
from unittest.mock import MagicMock, patch

import pytest

# All tests in this file patch `app.mt5_client.mt5` so the real
# MetaTrader5 package (Windows-only) is never required.


def _make_mt5_mock() -> MagicMock:
    """Return a fully configured mock of the MetaTrader5 module."""
    m = MagicMock()
    m.initialize.return_value = True
    m.shutdown.return_value = None
    m.last_error.return_value = (0, "OK")
    m.TRADE_RETCODE_DONE = 10009
    # Default account info
    m.account_info.return_value = SimpleNamespace(
        login=12345678,
        server="Exness-MT5Demo2",
        balance=10000.0,
        equity=10050.0,
        margin=100.0,
        margin_free=9950.0,
        margin_level=10050.0,
        leverage=500,
        currency="USD",
        profit=50.0,
    )
    # Default terminal info (connected)
    m.terminal_info.return_value = SimpleNamespace(connected=True)
    return m


@pytest.fixture
def mt5_mock():
    mock = _make_mt5_mock()
    with patch("app.mt5_client.mt5", mock), patch("app.mt5_client.MT5_AVAILABLE", True):
        yield mock


@pytest.fixture
def client(mt5_mock):
    from app.mt5_client import MT5Client, MT5Config

    config = MT5Config(login=12345678, password="demo_pass", server="Exness-MT5Demo2")
    c = MT5Client(config)
    c._connected = True  # simulate already connected
    return c


class TestConnect:
    def test_connect_success(self, mt5_mock):
        from app.mt5_client import MT5Client, MT5Config

        config = MT5Config(login=12345678, password="demo_pass", server="Exness-MT5Demo2")
        c = MT5Client(config)
        assert c.connect() is True
        assert c._connected is True
        mt5_mock.initialize.assert_called_once()

    def test_connect_failure(self, mt5_mock):
        mt5_mock.initialize.return_value = False
        mt5_mock.last_error.return_value = (5, "Authorization failed")
        from app.mt5_client import MT5Client, MT5Config

        config = MT5Config(login=0, password="wrong", server="Bad-Server")
        c = MT5Client(config)
        assert c.connect() is False
        assert c._connected is False

    def test_connect_unavailable(self):
        with patch("app.mt5_client.MT5_AVAILABLE", False):
            from app.mt5_client import MT5Client, MT5Config

            config = MT5Config(login=12345, password="p", server="s")
            c = MT5Client(config)
            assert c.connect() is False

    def test_disconnect(self, mt5_mock, client):
        client.disconnect()
        mt5_mock.shutdown.assert_called_once()
        assert client._connected is False

    def test_is_connected_true(self, mt5_mock, client):
        assert client.is_connected() is True

    def test_is_connected_false_when_terminal_not_connected(self, mt5_mock, client):
        mt5_mock.terminal_info.return_value = SimpleNamespace(connected=False)
        assert client.is_connected() is False

    def test_is_connected_false_when_terminal_info_none(self, mt5_mock, client):
        mt5_mock.terminal_info.return_value = None
        assert client.is_connected() is False

    def test_ensure_connected_raises_on_failure(self, mt5_mock):
        mt5_mock.initialize.return_value = False
        from app.mt5_client import MT5Client, MT5Config

        config = MT5Config(login=0, password="", server="")
        c = MT5Client(config)
        with pytest.raises(ConnectionError):
            with c.ensure_connected():
                pass


class TestGetAccountInfo:
    def test_returns_account_dict(self, mt5_mock, client):
        info = client.get_account_info()
        assert info["login"] == 12345678
        assert info["server"] == "Exness-MT5Demo2"
        assert info["balance"] == 10000.0
        assert info["equity"] == 10050.0
        assert info["margin"] == 100.0
        assert info["free_margin"] == 9950.0
        assert info["margin_level"] == 10050.0
        assert info["leverage"] == 500
        assert info["currency"] == "USD"
        assert info["profit"] == 50.0

    def test_returns_empty_when_none(self, mt5_mock, client):
        mt5_mock.account_info.return_value = None
        info = client.get_account_info()
        assert info == {}


class TestGetSymbolInfo:
    def test_returns_symbol_dict(self, mt5_mock, client):
        mt5_mock.symbol_info.return_value = SimpleNamespace(
            name="EURUSD",
            bid=1.09120,
            ask=1.09122,
            spread=2,
            digits=5,
            point=0.00001,
            volume_min=0.01,
            volume_max=200.0,
            volume_step=0.01,
            trade_contract_size=100000.0,
            swap_long=-3.5,
            swap_short=1.2,
        )
        mt5_mock.symbol_info_tick.return_value = SimpleNamespace(bid=1.09118, ask=1.09120)

        info = client.get_symbol_info("EURUSD")
        assert info is not None
        assert info["symbol"] == "EURUSD"
        assert info["bid"] == 1.09118  # from tick
        assert info["spread"] == 2
        assert info["min_lot"] == 0.01

    def test_returns_none_when_symbol_not_found(self, mt5_mock, client):
        mt5_mock.symbol_info.return_value = None
        info = client.get_symbol_info("INVALID")
        assert info is None


class TestPlaceOrder:
    def _make_order_result(self, retcode=10009, order=100001, deal=200001, volume=0.1, price=1.09):
        return SimpleNamespace(
            retcode=retcode, order=order, deal=deal, volume=volume, price=price, comment="Done"
        )

    def test_place_market_buy(self, mt5_mock, client):
        mt5_mock.symbol_select.return_value = True
        mt5_mock.symbol_info_tick.return_value = SimpleNamespace(bid=1.09120, ask=1.09122)
        mt5_mock.order_send.return_value = self._make_order_result()

        from app.mt5_client import OrderType

        result = client.place_order("EURUSD", OrderType.BUY, 0.1)
        assert result.success is True
        assert result.order_id == 100001
        assert result.deal_id == 200001

    def test_place_market_sell(self, mt5_mock, client):
        mt5_mock.symbol_select.return_value = True
        mt5_mock.symbol_info_tick.return_value = SimpleNamespace(bid=1.09120, ask=1.09122)
        mt5_mock.order_send.return_value = self._make_order_result(price=1.09120)

        from app.mt5_client import OrderType

        result = client.place_order("EURUSD", OrderType.SELL, 0.1)
        assert result.success is True

    def test_place_limit_order(self, mt5_mock, client):
        mt5_mock.symbol_select.return_value = True
        mt5_mock.symbol_info_tick.return_value = SimpleNamespace(bid=1.09120, ask=1.09122)
        mt5_mock.order_send.return_value = self._make_order_result(price=1.085)

        from app.mt5_client import OrderType

        result = client.place_order("EURUSD", OrderType.BUY_LIMIT, 0.1, price=1.085)
        assert result.success is True
        # Verify pending action used
        call_request = mt5_mock.order_send.call_args[0][0]
        assert call_request["action"] == 5  # TRADE_ACTION_PENDING

    def test_place_order_with_sl_tp(self, mt5_mock, client):
        mt5_mock.symbol_select.return_value = True
        mt5_mock.symbol_info_tick.return_value = SimpleNamespace(bid=1.09120, ask=1.09122)
        mt5_mock.order_send.return_value = self._make_order_result()

        from app.mt5_client import OrderType

        result = client.place_order("EURUSD", OrderType.BUY, 0.1, sl=1.085, tp=1.100)
        assert result.success is True
        call_request = mt5_mock.order_send.call_args[0][0]
        assert call_request["sl"] == 1.085
        assert call_request["tp"] == 1.100

    def test_place_order_symbol_select_fails(self, mt5_mock, client):
        mt5_mock.symbol_select.return_value = False

        from app.mt5_client import OrderType

        result = client.place_order("INVALID", OrderType.BUY, 0.1)
        assert result.success is False
        assert "Failed to select symbol" in result.error_message

    def test_place_order_tick_fails(self, mt5_mock, client):
        mt5_mock.symbol_select.return_value = True
        mt5_mock.symbol_info_tick.return_value = None

        from app.mt5_client import OrderType

        result = client.place_order("EURUSD", OrderType.BUY, 0.1)
        assert result.success is False
        assert "Failed to get tick" in result.error_message

    def test_place_order_send_fails(self, mt5_mock, client):
        mt5_mock.symbol_select.return_value = True
        mt5_mock.symbol_info_tick.return_value = SimpleNamespace(bid=1.09120, ask=1.09122)
        mt5_mock.order_send.return_value = SimpleNamespace(
            retcode=10018, comment="Not enough money"
        )

        from app.mt5_client import OrderType

        result = client.place_order("EURUSD", OrderType.BUY, 100.0)
        assert result.success is False
        assert result.error_code == 10018

    def test_place_order_send_returns_none(self, mt5_mock, client):
        mt5_mock.symbol_select.return_value = True
        mt5_mock.symbol_info_tick.return_value = SimpleNamespace(bid=1.09120, ask=1.09122)
        mt5_mock.order_send.return_value = None
        mt5_mock.last_error.return_value = (1, "Network error")

        from app.mt5_client import OrderType

        result = client.place_order("EURUSD", OrderType.BUY, 0.1)
        assert result.success is False
        assert result.error_code == 1


class TestModifyPosition:
    def test_modify_sl_tp(self, mt5_mock, client):
        mt5_mock.positions_get.return_value = [
            SimpleNamespace(ticket=300001, symbol="EURUSD", sl=1.085, tp=1.100)
        ]
        mt5_mock.order_send.return_value = SimpleNamespace(retcode=10009, comment="Done")

        result = client.modify_position(300001, sl=1.088, tp=1.105)
        assert result.success is True
        assert result.order_id == 300001

    def test_modify_position_not_found(self, mt5_mock, client):
        mt5_mock.positions_get.return_value = []
        result = client.modify_position(999)
        assert result.success is False
        assert "not found" in result.error_message

    def test_modify_preserves_existing_sl_when_only_tp_given(self, mt5_mock, client):
        mt5_mock.positions_get.return_value = [
            SimpleNamespace(ticket=300001, symbol="EURUSD", sl=1.085, tp=1.100)
        ]
        mt5_mock.order_send.return_value = SimpleNamespace(retcode=10009, comment="Done")

        client.modify_position(300001, tp=1.110)
        call_request = mt5_mock.order_send.call_args[0][0]
        assert call_request["sl"] == 1.085  # preserved
        assert call_request["tp"] == 1.110  # updated


class TestClosePosition:
    def test_close_full_position(self, mt5_mock, client):
        mt5_mock.positions_get.return_value = [
            SimpleNamespace(
                ticket=300001, symbol="EURUSD", type=0, volume=0.1, magic=123456
            )  # type=0 is BUY
        ]
        mt5_mock.symbol_info_tick.return_value = SimpleNamespace(bid=1.09200, ask=1.09202)
        mt5_mock.order_send.return_value = SimpleNamespace(
            retcode=10009, deal=500001, volume=0.1, price=1.09200
        )

        result = client.close_position(300001)
        assert result.success is True
        assert result.deal_id == 500001
        assert result.volume == 0.1

    def test_close_partial_position(self, mt5_mock, client):
        mt5_mock.positions_get.return_value = [
            SimpleNamespace(ticket=300001, symbol="EURUSD", type=0, volume=0.2, magic=123456)
        ]
        mt5_mock.symbol_info_tick.return_value = SimpleNamespace(bid=1.09200, ask=1.09202)
        mt5_mock.order_send.return_value = SimpleNamespace(
            retcode=10009, deal=500002, volume=0.1, price=1.09200
        )

        result = client.close_position(300001, volume=0.1)
        assert result.success is True
        call_request = mt5_mock.order_send.call_args[0][0]
        assert call_request["volume"] == 0.1

    def test_close_sell_position_uses_buy_type(self, mt5_mock, client):
        mt5_mock.positions_get.return_value = [
            SimpleNamespace(
                ticket=300002, symbol="EURUSD", type=1, volume=0.1, magic=123456
            )  # type=1 is SELL
        ]
        mt5_mock.symbol_info_tick.return_value = SimpleNamespace(bid=1.09200, ask=1.09202)
        mt5_mock.order_send.return_value = SimpleNamespace(
            retcode=10009, deal=500003, volume=0.1, price=1.09202
        )

        result = client.close_position(300002)
        assert result.success is True
        call_request = mt5_mock.order_send.call_args[0][0]
        assert call_request["type"] == 0  # BUY to close SELL

    def test_close_position_not_found(self, mt5_mock, client):
        mt5_mock.positions_get.return_value = []
        result = client.close_position(999)
        assert result.success is False
        assert "not found" in result.error_message


class TestCloseAllPositions:
    def test_close_all_returns_empty_when_no_positions(self, mt5_mock, client):
        mt5_mock.positions_get.return_value = []
        results = client.close_all_positions()
        assert results == []

    def test_close_all_closes_each_position(self, mt5_mock, client):
        mt5_mock.positions_get.side_effect = [
            # First call: get all positions
            [
                SimpleNamespace(ticket=300001, symbol="EURUSD", type=0, volume=0.1, magic=123456),
                SimpleNamespace(ticket=300002, symbol="GBPUSD", type=1, volume=0.2, magic=123456),
            ],
            # Subsequent calls: each close_position calls positions_get(ticket=...)
            [SimpleNamespace(ticket=300001, symbol="EURUSD", type=0, volume=0.1, magic=123456)],
            [SimpleNamespace(ticket=300002, symbol="GBPUSD", type=1, volume=0.2, magic=123456)],
        ]
        mt5_mock.symbol_info_tick.return_value = SimpleNamespace(bid=1.09200, ask=1.09202)
        mt5_mock.order_send.return_value = SimpleNamespace(
            retcode=10009, deal=500001, volume=0.1, price=1.09200
        )

        results = client.close_all_positions()
        assert len(results) == 2
        assert all(r.success for r in results)


class TestCancelOrder:
    def test_cancel_pending_order(self, mt5_mock, client):
        mt5_mock.order_send.return_value = SimpleNamespace(retcode=10009, comment="Done")
        result = client.cancel_order(100002)
        assert result.success is True
        assert result.order_id == 100002

    def test_cancel_order_failure(self, mt5_mock, client):
        mt5_mock.order_send.return_value = SimpleNamespace(
            retcode=10006, comment="Request rejected"
        )
        result = client.cancel_order(999)
        assert result.success is False
        assert result.error_code == 10006


class TestGetPositions:
    def test_returns_empty_list(self, mt5_mock, client):
        mt5_mock.positions_get.return_value = []
        positions = client.get_positions()
        assert positions == []

    def test_returns_none_as_empty(self, mt5_mock, client):
        mt5_mock.positions_get.return_value = None
        positions = client.get_positions()
        assert positions == []

    def test_returns_position_dicts(self, mt5_mock, client):
        mt5_mock.positions_get.return_value = [
            SimpleNamespace(
                ticket=300001,
                symbol="EURUSD",
                type=0,
                volume=0.1,
                price_open=1.09000,
                price_current=1.09200,
                sl=1.085,
                tp=1.100,
                profit=20.0,
                swap=-0.5,
                magic=123456,
                comment="Tradie",
                time=1700000000,
            )
        ]
        positions = client.get_positions()
        assert len(positions) == 1
        assert positions[0]["type"] == "BUY"
        assert positions[0]["symbol"] == "EURUSD"


class TestGetPendingOrders:
    def test_returns_pending_order_dicts(self, mt5_mock, client):
        mt5_mock.orders_get.return_value = [
            SimpleNamespace(
                ticket=400001,
                symbol="GBPUSD",
                type=2,  # BUY_LIMIT
                volume_current=0.1,
                price_open=1.265,
                sl=1.260,
                tp=1.280,
                magic=123456,
                comment="Tradie",
            )
        ]
        orders = client.get_pending_orders()
        assert len(orders) == 1
        assert orders[0]["type"] == "BUY_LIMIT"
        assert orders[0]["price"] == 1.265

    def test_returns_empty_when_none(self, mt5_mock, client):
        mt5_mock.orders_get.return_value = None
        orders = client.get_pending_orders()
        assert orders == []
