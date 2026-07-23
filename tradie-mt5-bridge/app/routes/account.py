from fastapi import APIRouter, HTTPException, Request

from ..models import AccountInfoResponse, SymbolInfoResponse
from ..mt5_client import MT5Client

router = APIRouter(prefix="/account", tags=["Account"])


@router.get("/info", response_model=AccountInfoResponse)
async def get_account_info(request: Request) -> AccountInfoResponse:
    """Get current account balance, equity, margin, and leverage."""
    client: MT5Client = request.app.state.mt5_client
    try:
        info = client.get_account_info()
    except ConnectionError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e
    if not info:
        raise HTTPException(status_code=503, detail="Failed to get account info from MT5")
    return AccountInfoResponse(**info)


@router.get("/symbol/{symbol}", response_model=SymbolInfoResponse)
async def get_symbol_info(symbol: str, request: Request) -> SymbolInfoResponse:
    """Get current bid/ask, spread, lot sizes, and swap rates for a symbol."""
    client: MT5Client = request.app.state.mt5_client
    try:
        info = client.get_symbol_info(symbol)
    except ConnectionError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e
    if info is None:
        raise HTTPException(status_code=404, detail=f"Symbol '{symbol}' not found")
    return SymbolInfoResponse(**info)
