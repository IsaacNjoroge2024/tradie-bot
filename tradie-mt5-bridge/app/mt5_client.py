import logging
from contextlib import contextmanager
from dataclasses import dataclass
from enum import IntEnum
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)

try:
    import MetaTrader5 as mt5  # Windows-only package

    MT5_AVAILABLE = True
except ImportError:
    mt5 = None  # type: ignore[assignment]
    MT5_AVAILABLE = False
    logger.warning("MetaTrader5 package is not available (Windows-only). MT5 operations will fail.")

# MT5 integer constants — stable across versions, match official MT5 SDK values.
_ORDER_TYPE_BUY = 0
_ORDER_TYPE_SELL = 1
_ORDER_TYPE_BUY_LIMIT = 2
_ORDER_TYPE_SELL_LIMIT = 3
_ORDER_TYPE_BUY_STOP = 4
_ORDER_TYPE_SELL_STOP = 5
_TRADE_ACTION_DEAL = 1
_TRADE_ACTION_PENDING = 5
_TRADE_ACTION_SLTP = 6
_TRADE_ACTION_REMOVE = 8
_TRADE_RETCODE_DONE = 10009
_ORDER_TIME_GTC = 1
_ORDER_FILLING_IOC = 1


class OrderType(IntEnum):
    BUY = _ORDER_TYPE_BUY
    SELL = _ORDER_TYPE_SELL
    BUY_LIMIT = _ORDER_TYPE_BUY_LIMIT
    SELL_LIMIT = _ORDER_TYPE_SELL_LIMIT
    BUY_STOP = _ORDER_TYPE_BUY_STOP
    SELL_STOP = _ORDER_TYPE_SELL_STOP


@dataclass
class MT5Config:
    login: int
    password: str
    server: str
    path: str = "/root/.wine/drive_c/Program Files/MetaTrader 5/terminal64.exe"
    timeout: int = 60000


@dataclass
class OrderResult:
    success: bool
    order_id: Optional[int] = None
    deal_id: Optional[int] = None
    volume: float = 0.0
    price: float = 0.0
    error_code: Optional[int] = None
    error_message: Optional[str] = None


class MT5Client:
    def __init__(self, config: MT5Config):
        self.config = config
        self._connected = False

    def connect(self) -> bool:
        if not MT5_AVAILABLE:
            logger.error("MetaTrader5 package is not available (Windows-only)")
            return False
        if not mt5.initialize(
            path=self.config.path,
            login=self.config.login,
            password=self.config.password,
            server=self.config.server,
            timeout=self.config.timeout,
        ):
            error = mt5.last_error()
            logger.error(f"MT5 initialize failed: {error}")
            return False

        self._connected = True
        account_info = mt5.account_info()
        logger.info(f"Connected to MT5: {account_info.server}, Balance: {account_info.balance}")
        return True

    def disconnect(self):
        if MT5_AVAILABLE and mt5 is not None:
            mt5.shutdown()
        self._connected = False
        logger.info("MT5 disconnected")

    def is_connected(self) -> bool:
        if not self._connected or not MT5_AVAILABLE:
            return False
        info = mt5.terminal_info()
        return info is not None and info.connected

    @contextmanager
    def ensure_connected(self):
        if not self.is_connected():
            if not self.connect():
                raise ConnectionError("Failed to connect to MT5")
        try:
            yield
        except Exception as e:
            logger.error(f"MT5 operation failed: {e}")
            raise

    def get_account_info(self) -> Dict[str, Any]:
        with self.ensure_connected():
            info = mt5.account_info()
            if info is None:
                return {}
            return {
                "login": info.login,
                "server": info.server,
                "balance": info.balance,
                "equity": info.equity,
                "margin": info.margin,
                "free_margin": info.margin_free,
                "margin_level": info.margin_level,
                "leverage": info.leverage,
                "currency": info.currency,
                "profit": info.profit,
            }

    def get_symbol_info(self, symbol: str) -> Optional[Dict[str, Any]]:
        with self.ensure_connected():
            mt5.symbol_select(symbol, True)
            info = mt5.symbol_info(symbol)
            if info is None:
                return None
            tick = mt5.symbol_info_tick(symbol)
            return {
                "symbol": info.name,
                "bid": tick.bid if tick else info.bid,
                "ask": tick.ask if tick else info.ask,
                "spread": info.spread,
                "digits": info.digits,
                "point": info.point,
                "min_lot": info.volume_min,
                "max_lot": info.volume_max,
                "lot_step": info.volume_step,
                "contract_size": info.trade_contract_size,
                "swap_long": info.swap_long,
                "swap_short": info.swap_short,
            }

    def place_order(
        self,
        symbol: str,
        order_type: OrderType,
        volume: float,
        price: Optional[float] = None,
        sl: Optional[float] = None,
        tp: Optional[float] = None,
        deviation: int = 20,
        magic: int = 123456,
        comment: str = "Tradie",
    ) -> OrderResult:
        with self.ensure_connected():
            if not mt5.symbol_select(symbol, True):
                return OrderResult(
                    success=False,
                    error_message=f"Failed to select symbol {symbol}",
                )

            tick = mt5.symbol_info_tick(symbol)
            if tick is None:
                return OrderResult(
                    success=False,
                    error_message=f"Failed to get tick for {symbol}",
                )

            if price is None:
                price = (
                    tick.ask
                    if order_type in (OrderType.BUY, OrderType.BUY_LIMIT, OrderType.BUY_STOP)
                    else tick.bid
                )

            is_market = order_type in (OrderType.BUY, OrderType.SELL)
            request: Dict[str, Any] = {
                "action": _TRADE_ACTION_DEAL if is_market else _TRADE_ACTION_PENDING,
                "symbol": symbol,
                "volume": volume,
                "type": int(order_type),
                "price": price,
                "deviation": deviation,
                "magic": magic,
                "comment": comment,
                "type_time": _ORDER_TIME_GTC,
                "type_filling": _ORDER_FILLING_IOC,
            }

            if sl is not None:
                request["sl"] = sl
            if tp is not None:
                request["tp"] = tp

            result = mt5.order_send(request)

            if result is None:
                error = mt5.last_error()
                return OrderResult(
                    success=False,
                    error_code=error[0],
                    error_message=error[1],
                )

            if result.retcode != _TRADE_RETCODE_DONE:
                return OrderResult(
                    success=False,
                    error_code=result.retcode,
                    error_message=result.comment,
                )

            return OrderResult(
                success=True,
                order_id=result.order,
                deal_id=result.deal,
                volume=result.volume,
                price=result.price,
            )

    def modify_position(
        self,
        ticket: int,
        sl: Optional[float] = None,
        tp: Optional[float] = None,
    ) -> OrderResult:
        with self.ensure_connected():
            position = mt5.positions_get(ticket=ticket)
            if not position:
                return OrderResult(
                    success=False,
                    error_message=f"Position {ticket} not found",
                )

            pos = position[0]
            request = {
                "action": _TRADE_ACTION_SLTP,
                "position": ticket,
                "symbol": pos.symbol,
                "sl": sl if sl is not None else pos.sl,
                "tp": tp if tp is not None else pos.tp,
            }

            result = mt5.order_send(request)

            if result is None or result.retcode != _TRADE_RETCODE_DONE:
                error = mt5.last_error() if result is None else (result.retcode, result.comment)
                return OrderResult(
                    success=False,
                    error_code=error[0],
                    error_message=str(error[1]),
                )

            return OrderResult(success=True, order_id=ticket)

    def close_position(self, ticket: int, volume: Optional[float] = None) -> OrderResult:
        with self.ensure_connected():
            position = mt5.positions_get(ticket=ticket)
            if not position:
                return OrderResult(
                    success=False,
                    error_message=f"Position {ticket} not found",
                )

            pos = position[0]
            close_volume = volume if volume is not None else pos.volume
            close_type = _ORDER_TYPE_SELL if pos.type == _ORDER_TYPE_BUY else _ORDER_TYPE_BUY

            tick = mt5.symbol_info_tick(pos.symbol)
            price = tick.bid if close_type == _ORDER_TYPE_SELL else tick.ask

            request = {
                "action": _TRADE_ACTION_DEAL,
                "position": ticket,
                "symbol": pos.symbol,
                "volume": close_volume,
                "type": close_type,
                "price": price,
                "deviation": 20,
                "magic": pos.magic,
                "comment": "Tradie close",
            }

            result = mt5.order_send(request)

            if result is None or result.retcode != _TRADE_RETCODE_DONE:
                error = mt5.last_error() if result is None else (result.retcode, result.comment)
                return OrderResult(
                    success=False,
                    error_code=error[0],
                    error_message=str(error[1]),
                )

            return OrderResult(
                success=True,
                deal_id=result.deal,
                volume=result.volume,
                price=result.price,
            )

    def close_all_positions(self, symbol: Optional[str] = None) -> List[OrderResult]:
        with self.ensure_connected():
            positions = mt5.positions_get(symbol=symbol) if symbol else mt5.positions_get()
            if not positions:
                return []
            return [self.close_position(pos.ticket) for pos in positions]

    def get_positions(self, symbol: Optional[str] = None) -> List[Dict[str, Any]]:
        with self.ensure_connected():
            positions = mt5.positions_get(symbol=symbol) if symbol else mt5.positions_get()
            if not positions:
                return []
            return [
                {
                    "ticket": pos.ticket,
                    "symbol": pos.symbol,
                    "type": "BUY" if pos.type == _ORDER_TYPE_BUY else "SELL",
                    "volume": pos.volume,
                    "open_price": pos.price_open,
                    "current_price": pos.price_current,
                    "sl": pos.sl,
                    "tp": pos.tp,
                    "profit": pos.profit,
                    "swap": pos.swap,
                    "magic": pos.magic,
                    "comment": pos.comment,
                    "open_time": pos.time,
                }
                for pos in positions
            ]

    def get_pending_orders(self, symbol: Optional[str] = None) -> List[Dict[str, Any]]:
        with self.ensure_connected():
            orders = mt5.orders_get(symbol=symbol) if symbol else mt5.orders_get()
            if not orders:
                return []
            order_type_map = {
                _ORDER_TYPE_BUY_LIMIT: "BUY_LIMIT",
                _ORDER_TYPE_SELL_LIMIT: "SELL_LIMIT",
                _ORDER_TYPE_BUY_STOP: "BUY_STOP",
                _ORDER_TYPE_SELL_STOP: "SELL_STOP",
            }
            return [
                {
                    "ticket": order.ticket,
                    "symbol": order.symbol,
                    "type": order_type_map.get(order.type, "UNKNOWN"),
                    "volume": order.volume_current,
                    "price": order.price_open,
                    "sl": order.sl,
                    "tp": order.tp,
                    "magic": order.magic,
                    "comment": order.comment,
                }
                for order in orders
            ]

    def cancel_order(self, ticket: int) -> OrderResult:
        with self.ensure_connected():
            request = {
                "action": _TRADE_ACTION_REMOVE,
                "order": ticket,
            }
            result = mt5.order_send(request)

            if result is None or result.retcode != _TRADE_RETCODE_DONE:
                error = mt5.last_error() if result is None else (result.retcode, result.comment)
                return OrderResult(
                    success=False,
                    error_code=error[0],
                    error_message=str(error[1]),
                )

            return OrderResult(success=True, order_id=ticket)
