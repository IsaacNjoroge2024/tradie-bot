from typing import Any, Dict, List, Literal, Optional

from pydantic import BaseModel, Field, field_validator


class PlaceOrderRequest(BaseModel):
    symbol: str = Field(..., description="Trading symbol, e.g. EURUSD")
    side: Literal["BUY", "SELL"] = Field(..., description="BUY or SELL")
    volume: float = Field(..., gt=0, description="Trade volume in lots")
    order_type: Literal["MARKET", "LIMIT", "STOP"] = Field(
        default="MARKET", description="MARKET, LIMIT, or STOP"
    )
    price: Optional[float] = Field(default=None, description="Required for LIMIT and STOP orders")
    sl: Optional[float] = Field(default=None, description="Stop loss price")
    tp: Optional[float] = Field(default=None, description="Take profit price")
    comment: str = Field(default="Tradie", description="Order comment")

    @field_validator("side", "order_type", mode="before")
    @classmethod
    def normalise_to_uppercase(cls, v: object) -> object:
        return v.upper() if isinstance(v, str) else v


class ModifyPositionRequest(BaseModel):
    ticket: int = Field(..., description="Position ticket number")
    sl: Optional[float] = Field(default=None, description="New stop loss price")
    tp: Optional[float] = Field(default=None, description="New take profit price")


class ClosePositionRequest(BaseModel):
    ticket: int = Field(..., description="Position ticket number")
    volume: Optional[float] = Field(default=None, description="Volume to close; None = full close")


class CancelOrderRequest(BaseModel):
    ticket: int = Field(..., description="Pending order ticket number")


class PlaceOrderResponse(BaseModel):
    success: bool
    order_id: Optional[int] = None
    deal_id: Optional[int] = None
    volume: float = 0.0
    price: float = 0.0


class ClosePositionResponse(BaseModel):
    success: bool
    deal_id: Optional[int] = None
    volume: float = 0.0
    price: float = 0.0


class CloseAllResponse(BaseModel):
    closed: int
    failed: int
    results: List[Dict[str, Any]]


class AccountInfoResponse(BaseModel):
    login: int
    server: str
    balance: float
    equity: float
    margin: float
    free_margin: float
    margin_level: float
    leverage: int
    currency: str
    profit: float


class SymbolInfoResponse(BaseModel):
    symbol: str
    bid: float
    ask: float
    spread: int
    digits: int
    point: float
    min_lot: float
    max_lot: float
    lot_step: float
    contract_size: float
    swap_long: float
    swap_short: float
