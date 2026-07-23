import logging
import time
from typing import Optional

from ..config import settings
from ..mt5_client import MT5Client, OrderResult, OrderType

logger = logging.getLogger(__name__)

_ORDER_TYPE_MAP = {
    ("BUY", "MARKET"): OrderType.BUY,
    ("SELL", "MARKET"): OrderType.SELL,
    ("BUY", "LIMIT"): OrderType.BUY_LIMIT,
    ("SELL", "LIMIT"): OrderType.SELL_LIMIT,
    ("BUY", "STOP"): OrderType.BUY_STOP,
    ("SELL", "STOP"): OrderType.SELL_STOP,
}


class OrderService:
    def __init__(self, client: MT5Client):
        self._client = client

    def place_order(
        self,
        symbol: str,
        side: str,
        volume: float,
        order_type: str = "MARKET",
        price: Optional[float] = None,
        sl: Optional[float] = None,
        tp: Optional[float] = None,
        comment: str = "Tradie",
    ) -> OrderResult:
        key = (side.upper(), order_type.upper())
        mt5_order_type = _ORDER_TYPE_MAP.get(key)
        if mt5_order_type is None:
            return OrderResult(
                success=False,
                error_message=f"Invalid order type: {side} {order_type}",
            )

        logger.info(f"Placing {side} {order_type} order: {symbol} vol={volume}")
        result = self._retry(
            lambda: self._client.place_order(
                symbol=symbol,
                order_type=mt5_order_type,
                volume=volume,
                price=price,
                sl=sl,
                tp=tp,
                magic=settings.mt5_magic,
                comment=comment,
            )
        )
        if result.success:
            logger.info(
                f"Order placed: order_id={result.order_id} deal_id={result.deal_id} "
                f"price={result.price} volume={result.volume}"
            )
        else:
            logger.error(f"Order failed: {result.error_code} - {result.error_message}")
        return result

    def cancel_order(self, ticket: int) -> OrderResult:
        logger.info(f"Cancelling pending order: ticket={ticket}")
        result = self._retry(lambda: self._client.cancel_order(ticket))
        if result.success:
            logger.info(f"Order cancelled: ticket={ticket}")
        else:
            logger.error(
                f"Cancel failed: ticket={ticket} {result.error_code} - {result.error_message}"
            )
        return result

    def _retry(self, func) -> OrderResult:
        """Retry on transient MT5 connection errors only."""
        max_retries = settings.mt5_max_retries
        retry_delay = settings.mt5_retry_delay_seconds
        for attempt in range(max_retries):
            try:
                return func()
            except ConnectionError as e:
                if attempt < max_retries - 1:
                    logger.warning(
                        f"MT5 connection error (attempt {attempt + 1}/{max_retries}): {e}. Retrying..."
                    )
                    time.sleep(retry_delay)
                else:
                    raise
        return OrderResult(success=False, error_message="All retry attempts exhausted")
