from typing import Any, Dict, List, Optional

from fastapi import APIRouter, HTTPException, Query, Request

from ..models import (
    CloseAllResponse,
    ClosePositionRequest,
    ClosePositionResponse,
    ModifyPositionRequest,
)
from ..services.position_service import PositionService

router = APIRouter(prefix="/positions", tags=["Positions"])


@router.get("/", response_model=List[Dict[str, Any]])
async def get_positions(
    request: Request,
    symbol: Optional[str] = Query(default=None, description="Filter by symbol"),
) -> List[Dict[str, Any]]:
    """Get all open positions."""
    service: PositionService = request.app.state.position_service
    try:
        return await service.get_positions(symbol=symbol)
    except ConnectionError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e


@router.get("/pending", response_model=List[Dict[str, Any]])
async def get_pending_orders(
    request: Request,
    symbol: Optional[str] = Query(default=None, description="Filter by symbol"),
) -> List[Dict[str, Any]]:
    """Get all pending orders."""
    service: PositionService = request.app.state.position_service
    try:
        return await service.get_pending_orders(symbol=symbol)
    except ConnectionError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e


@router.post("/modify")
async def modify_position(body: ModifyPositionRequest, request: Request) -> dict:
    """Modify stop loss and/or take profit on an existing position."""
    service: PositionService = request.app.state.position_service
    try:
        result = await service.modify_position(ticket=body.ticket, sl=body.sl, tp=body.tp)
    except ConnectionError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e
    if not result.success:
        raise HTTPException(status_code=400, detail=result.error_message)
    return {"success": True, "ticket": body.ticket}


@router.post("/close", response_model=ClosePositionResponse)
async def close_position(body: ClosePositionRequest, request: Request) -> ClosePositionResponse:
    """Close a position fully or partially."""
    service: PositionService = request.app.state.position_service
    try:
        result = await service.close_position(ticket=body.ticket, volume=body.volume)
    except ConnectionError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e
    if not result.success:
        raise HTTPException(status_code=400, detail=result.error_message)
    return ClosePositionResponse(
        success=True,
        deal_id=result.deal_id,
        volume=result.volume,
        price=result.price,
    )


@router.post("/close-all", response_model=CloseAllResponse)
async def close_all_positions(
    request: Request,
    symbol: Optional[str] = Query(
        default=None, description="Close only positions for this symbol; omit for all"
    ),
) -> CloseAllResponse:
    """Close all open positions (kill switch). Optionally filter by symbol."""
    service: PositionService = request.app.state.position_service
    try:
        results = await service.close_all_positions(symbol=symbol)
    except ConnectionError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e
    closed = sum(1 for r in results if r.success)
    failed = len(results) - closed
    return CloseAllResponse(
        closed=closed,
        failed=failed,
        results=[{"success": r.success, "error": r.error_message} for r in results],
    )
