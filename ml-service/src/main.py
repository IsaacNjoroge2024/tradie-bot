import logging
import time
from contextlib import asynccontextmanager

import numpy as np
import pandas as pd
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

from .api import regime_routes
from .config import settings
from .regime.hmm_detector import HMMRegimeDetector
from .regime.regime_service import RegimeService

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def _warmup_hmm() -> None:
    """Fit a throwaway HMM on synthetic data once, at startup.

    hmmlearn's first-ever fit in a freshly started process pays a one-time
    ~1.8s BLAS/LAPACK thread-pool cold-start cost (observed on Windows);
    every fit after that — even for a brand-new symbol — takes ~30ms. Paying
    that cost here, before the app starts accepting traffic, keeps it off
    the first real /api/regime/detect request.
    """
    rng = np.random.default_rng(0)
    n = 80
    returns = rng.normal(0.0005, 0.01, n)
    close = 100 * np.cumprod(1 + returns)
    dummy = pd.DataFrame(
        {
            "close": close,
            "high": close * 1.005,
            "low": close * 0.995,
            "volume": rng.uniform(1_000_000, 2_000_000, n),
        }
    )

    start = time.perf_counter()
    HMMRegimeDetector().fit(dummy)
    elapsed_ms = (time.perf_counter() - start) * 1000
    logger.info("HMM warm-up fit complete in %.0f ms", elapsed_ms)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting ML Service...")
    app.state.regime_service = RegimeService()
    try:
        _warmup_hmm()
    except Exception:
        logger.warning(
            "HMM warm-up fit failed; first real request will pay the cold-start cost",
            exc_info=True,
        )
    logger.info("ML Service ready")
    yield
    logger.info("Shutting down ML Service...")


app = FastAPI(
    title="Tradie ML Service",
    description="Machine learning models for adaptive trading — market regime detection and beyond.",
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

app.include_router(regime_routes.router)


@app.get("/health")
async def health():
    return {"status": "healthy", "service": "ml-service"}


@app.get("/metrics", include_in_schema=False)
async def metrics():
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)
