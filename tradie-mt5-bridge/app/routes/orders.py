from fastapi import APIRouter, HTTPException, Request

from ..models import CancelOrderRequest, PlaceOrderRequest, PlaceOrderResponse
from ..services.order_service import OrderService

router = APIRouter(prefix="/orders", tags=["Orders"])


@router.post("/place", response_model=PlaceOrderResponse)
async def place_order(body: PlaceOrderRequest, request: Request) -> PlaceOrderResponse:
    """Place a new market, limit, or stop order."""
    service: OrderService = request.app.state.order_service
    result = service.place_order(
        symbol=body.symbol,
        side=body.side,
        volume=body.volume,
        order_type=body.order_type,
        price=body.price,
        sl=body.sl,
        tp=body.tp,
        comment=body.comment,
    )
    if not result.success:
        raise HTTPException(status_code=400, detail=result.error_message)
    return PlaceOrderResponse(
        success=True,
        order_id=result.order_id,
        deal_id=result.deal_id,
        volume=result.volume,
        price=result.price,
    )


@router.post("/cancel")
async def cancel_order(body: CancelOrderRequest, request: Request) -> dict:
    """Cancel a pending order."""
    service: OrderService = request.app.state.order_service
    result = service.cancel_order(ticket=body.ticket)
    if not result.success:
        raise HTTPException(status_code=400, detail=result.error_message)
    return {"success": True, "ticket": body.ticket}
