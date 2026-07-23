import asyncio

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse

from ..mt5_client import MT5_AVAILABLE, MT5Client

router = APIRouter(tags=["Health"])


@router.get("/health")
async def health(request: Request):
    """Health check — reports MT5 connection status."""
    client: MT5Client = request.app.state.mt5_client
    connected = await asyncio.to_thread(client.is_connected)
    body = {
        "status": "healthy" if connected else "degraded",
        "service": "tradie-mt5-bridge",
        "mt5_connected": connected,
        "mt5_available": MT5_AVAILABLE,
    }
    if not connected:
        return JSONResponse(status_code=503, content=body)
    return body
