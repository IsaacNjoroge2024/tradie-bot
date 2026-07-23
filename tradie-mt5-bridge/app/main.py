import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

from .config import settings
from .mt5_client import MT5Client, MT5Config
from .routes import account, health, orders, positions
from .services.order_service import OrderService
from .services.position_service import PositionService

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting MT5 Bridge service...")
    config = MT5Config(
        login=settings.mt5_login,
        password=settings.mt5_password.get_secret_value(),
        server=settings.mt5_server,
        path=settings.mt5_path,
        timeout=settings.mt5_timeout,
    )
    client = MT5Client(config)

    if settings.mt5_login:
        if client.connect():
            logger.info("MT5 Bridge ready — MT5 connected")
        else:
            logger.warning(
                "MT5 Bridge started without an MT5 connection. "
                "Trading endpoints require a live MT5 terminal."
            )
    else:
        logger.warning("MT5 credentials not configured. Running in degraded mode.")

    app.state.mt5_client = client
    app.state.order_service = OrderService(client)
    app.state.position_service = PositionService(client)

    yield

    logger.info("Shutting down MT5 Bridge...")
    client.disconnect()


app = FastAPI(
    title="Tradie MT5 Bridge",
    description=(
        "FastAPI bridge for MetaTrader 5 broker connectivity. "
        "Supports Exness, FxPro, and EGM Securities for forex trading."
    ),
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_allowed_origins,
    allow_credentials=True,
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type", "Authorization"],
)

app.include_router(health.router)
app.include_router(orders.router, prefix="/api")
app.include_router(positions.router, prefix="/api")
app.include_router(account.router, prefix="/api")


@app.get("/metrics", include_in_schema=False)
async def metrics():
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)
