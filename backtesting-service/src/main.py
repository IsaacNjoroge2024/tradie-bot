import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, Response
from prometheus_client import generate_latest, CONTENT_TYPE_LATEST

from .config import settings
from .data.loader import HistoricalDataLoader
from .data.timescale import TimescaleDBPool
from .api.monte_carlo_routes import router as monte_carlo_router
from .routers import backtest

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting Backtesting Service...")

    db_pool = TimescaleDBPool()
    await db_pool.initialize()

    app.state.db_pool = db_pool
    app.state.data_loader = HistoricalDataLoader(db_pool)

    if db_pool.is_connected:
        logger.info("Backtesting Service ready — TimescaleDB connected")
    else:
        logger.warning(
            "Backtesting Service started without a database connection. "
            "Backtest endpoints require TimescaleDB to load historical data."
        )

    yield

    logger.info("Shutting down Backtesting Service...")
    await db_pool.close()


app = FastAPI(
    title="Tradie Backtesting Service",
    description=(
        "Strategy backtesting, parameter optimisation, walk-forward analysis, "
        "Monte Carlo simulation and report generation."
    ),
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_allowed_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(backtest.router, prefix="/api", tags=["Backtesting"])
app.include_router(monte_carlo_router)


@app.get("/health")
async def health(request: Request):
    db_pool: TimescaleDBPool = getattr(request.app.state, "db_pool", None)
    db_connected = db_pool.is_connected if db_pool is not None else False
    if db_connected:
        return {"status": "healthy", "db_connected": True, "service": "backtesting-service"}
    return JSONResponse(
        status_code=503,
        content={"status": "degraded", "db_connected": False, "service": "backtesting-service"},
    )


@app.get("/metrics", include_in_schema=False)
async def metrics():
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)
