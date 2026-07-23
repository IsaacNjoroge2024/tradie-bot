from unittest.mock import MagicMock, patch

import pytest

from app.config import settings
from app.mt5_client import MT5Client, OrderResult, OrderType
from app.services.order_service import OrderService


@pytest.fixture
def mock_client():
    return MagicMock(spec=MT5Client)


@pytest.fixture
def service(mock_client):
    return OrderService(mock_client)


class TestPlaceOrder:
    async def test_place_market_buy(self, service, mock_client):
        mock_client.place_order.return_value = OrderResult(
            success=True, order_id=100001, deal_id=200001, volume=0.1, price=1.09122
        )

        result = await service.place_order("EURUSD", "BUY", 0.1, sl=1.085, tp=1.100)

        assert result.success is True
        assert result.order_id == 100001
        mock_client.place_order.assert_called_once_with(
            symbol="EURUSD",
            order_type=OrderType.BUY,
            volume=0.1,
            price=None,
            sl=1.085,
            tp=1.100,
            magic=settings.mt5_magic,
            comment="Tradie",
        )

    async def test_place_market_sell(self, service, mock_client):
        mock_client.place_order.return_value = OrderResult(
            success=True, order_id=100002, deal_id=200002, volume=0.2, price=1.09118
        )

        result = await service.place_order("EURUSD", "SELL", 0.2)

        assert result.success is True
        call_kwargs = mock_client.place_order.call_args[1]
        assert call_kwargs["order_type"] == OrderType.SELL

    async def test_place_limit_order(self, service, mock_client):
        mock_client.place_order.return_value = OrderResult(
            success=True, order_id=100003, volume=0.1, price=1.085
        )

        result = await service.place_order("EURUSD", "BUY", 0.1, order_type="LIMIT", price=1.085)

        assert result.success is True
        call_kwargs = mock_client.place_order.call_args[1]
        assert call_kwargs["order_type"] == OrderType.BUY_LIMIT
        assert call_kwargs["price"] == 1.085

    async def test_place_stop_order(self, service, mock_client):
        mock_client.place_order.return_value = OrderResult(success=True, order_id=100004)

        result = await service.place_order("EURUSD", "SELL", 0.1, order_type="STOP", price=1.080)

        call_kwargs = mock_client.place_order.call_args[1]
        assert call_kwargs["order_type"] == OrderType.SELL_STOP

    async def test_invalid_side_returns_error(self, service, mock_client):
        result = await service.place_order("EURUSD", "INVALID", 0.1)
        assert result.success is False
        assert "Invalid order type" in result.error_message
        mock_client.place_order.assert_not_called()

    async def test_invalid_order_type_returns_error(self, service, mock_client):
        result = await service.place_order("EURUSD", "BUY", 0.1, order_type="FUTURES")
        assert result.success is False
        mock_client.place_order.assert_not_called()

    async def test_side_is_case_insensitive(self, service, mock_client):
        mock_client.place_order.return_value = OrderResult(success=True, order_id=100005)
        result = await service.place_order("EURUSD", "buy", 0.1)
        assert result.success is True
        call_kwargs = mock_client.place_order.call_args[1]
        assert call_kwargs["order_type"] == OrderType.BUY

    async def test_order_type_is_case_insensitive(self, service, mock_client):
        mock_client.place_order.return_value = OrderResult(success=True, order_id=100006)
        result = await service.place_order("EURUSD", "BUY", 0.1, order_type="limit", price=1.085)
        call_kwargs = mock_client.place_order.call_args[1]
        assert call_kwargs["order_type"] == OrderType.BUY_LIMIT

    async def test_failure_result_is_returned(self, service, mock_client):
        mock_client.place_order.return_value = OrderResult(
            success=False, error_code=10018, error_message="Not enough money"
        )
        result = await service.place_order("EURUSD", "BUY", 100.0)
        assert result.success is False
        assert result.error_code == 10018

    async def test_custom_comment(self, service, mock_client):
        mock_client.place_order.return_value = OrderResult(success=True, order_id=100007)
        await service.place_order("EURUSD", "BUY", 0.1, comment="ICT Signal")
        call_kwargs = mock_client.place_order.call_args[1]
        assert call_kwargs["comment"] == "ICT Signal"


class TestCancelOrder:
    async def test_cancel_success(self, service, mock_client):
        mock_client.cancel_order.return_value = OrderResult(success=True, order_id=100002)
        result = await service.cancel_order(100002)
        assert result.success is True
        mock_client.cancel_order.assert_called_once_with(100002)

    async def test_cancel_failure(self, service, mock_client):
        mock_client.cancel_order.return_value = OrderResult(
            success=False, error_code=10006, error_message="Request rejected"
        )
        result = await service.cancel_order(999)
        assert result.success is False
        assert result.error_code == 10006


class TestRetry:
    async def test_retry_on_connection_error(self, service, mock_client):
        mock_client.place_order.side_effect = [
            ConnectionError("Disconnected"),
            OrderResult(success=True, order_id=100001, deal_id=200001, volume=0.1, price=1.09),
        ]

        with patch("app.services.order_service.time.sleep"):
            with patch("app.services.order_service.settings") as mock_settings:
                mock_settings.mt5_max_retries = 3
                mock_settings.mt5_retry_delay_seconds = 0.0
                mock_settings.mt5_magic = 123456
                result = await service.place_order("EURUSD", "BUY", 0.1)

        assert result.success is True
        assert mock_client.place_order.call_count == 2

    async def test_exhausts_retries_on_persistent_connection_error(self, service, mock_client):
        mock_client.cancel_order.side_effect = ConnectionError("Disconnected")

        with patch("app.services.order_service.time.sleep"):
            with patch("app.services.order_service.settings") as mock_settings:
                mock_settings.mt5_max_retries = 2
                mock_settings.mt5_retry_delay_seconds = 0.0
                with pytest.raises(ConnectionError, match="Disconnected"):
                    await service.cancel_order(100002)

        assert mock_client.cancel_order.call_count == 2

    async def test_no_retry_on_business_logic_failure(self, service, mock_client):
        mock_client.place_order.return_value = OrderResult(
            success=False, error_code=10018, error_message="Not enough money"
        )

        with patch("app.services.order_service.settings") as mock_settings:
            mock_settings.mt5_max_retries = 3
            mock_settings.mt5_retry_delay_seconds = 0.0
            mock_settings.mt5_magic = 123456
            result = await service.place_order("EURUSD", "BUY", 100.0)

        # Business logic failure — should not retry
        assert mock_client.place_order.call_count == 1
        assert result.success is False
