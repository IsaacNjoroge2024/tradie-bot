import logging
from typing import Any, Dict, List, Optional

from ..mt5_client import MT5Client, OrderResult

logger = logging.getLogger(__name__)


class PositionService:
    def __init__(self, client: MT5Client):
        self._client = client

    def get_positions(self, symbol: Optional[str] = None) -> List[Dict[str, Any]]:
        try:
            return self._client.get_positions(symbol=symbol)
        except ConnectionError as e:
            logger.error(f"Failed to get positions: {e}")
            raise

    def get_pending_orders(self, symbol: Optional[str] = None) -> List[Dict[str, Any]]:
        try:
            return self._client.get_pending_orders(symbol=symbol)
        except ConnectionError as e:
            logger.error(f"Failed to get pending orders: {e}")
            raise

    def modify_position(
        self,
        ticket: int,
        sl: Optional[float] = None,
        tp: Optional[float] = None,
    ) -> OrderResult:
        logger.info(f"Modifying position: ticket={ticket} sl={sl} tp={tp}")
        try:
            result = self._client.modify_position(ticket=ticket, sl=sl, tp=tp)
        except ConnectionError as e:
            logger.error(f"Failed to modify position {ticket}: {e}")
            raise
        if result.success:
            logger.info(f"Position modified: ticket={ticket}")
        else:
            logger.error(
                f"Modify failed: ticket={ticket} {result.error_code} - {result.error_message}"
            )
        return result

    def close_position(self, ticket: int, volume: Optional[float] = None) -> OrderResult:
        logger.info(f"Closing position: ticket={ticket} volume={volume}")
        try:
            result = self._client.close_position(ticket=ticket, volume=volume)
        except ConnectionError as e:
            logger.error(f"Failed to close position {ticket}: {e}")
            raise
        if result.success:
            logger.info(
                f"Position closed: ticket={ticket} deal_id={result.deal_id} "
                f"volume={result.volume} price={result.price}"
            )
        else:
            logger.error(
                f"Close failed: ticket={ticket} {result.error_code} - {result.error_message}"
            )
        return result

    def close_all_positions(self, symbol: Optional[str] = None) -> List[OrderResult]:
        logger.info(f"Closing all positions: symbol={symbol}")
        try:
            results = self._client.close_all_positions(symbol=symbol)
        except ConnectionError as e:
            logger.error(f"Failed to close all positions: {e}")
            raise
        closed = sum(1 for r in results if r.success)
        failed = len(results) - closed
        logger.info(f"Close all complete: closed={closed} failed={failed}")
        return results
