import asyncio

import pandas as pd
from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel

from ..config import settings
from ..regime.models import RegimeRecommendation, RegimeState
from ..regime.regime_service import RegimeService

router = APIRouter(prefix="/api/regime", tags=["Regime Detection"])


class PriceData(BaseModel):
    timestamp: list[str]
    open: list[float]
    high: list[float]
    low: list[float]
    close: list[float]
    volume: list[float]


class RegimeRequest(BaseModel):
    symbol: str
    timeframe: str
    price_data: PriceData


def _detect(
    service: RegimeService, symbol: str, timeframe: str, df: pd.DataFrame
) -> tuple[RegimeState, RegimeRecommendation]:
    """Runs the (potentially CPU-bound, e.g. on a retrain) regime fit/predict + recommendation."""
    state = service.get_regime(symbol, timeframe, df)
    recommendation = service.detectors[f"{symbol}_{timeframe}"].get_recommendation(state)
    return state, recommendation


@router.post("/detect")
async def detect_regime(request: Request, body: RegimeRequest):
    """Detect current market regime"""

    df = pd.DataFrame(
        {
            "timestamp": body.price_data.timestamp,
            "open": body.price_data.open,
            "high": body.price_data.high,
            "low": body.price_data.low,
            "close": body.price_data.close,
            "volume": body.price_data.volume,
        }
    )

    if len(df) < settings.regime_min_bars:
        raise HTTPException(
            400, f"Need at least {settings.regime_min_bars} bars for regime detection"
        )

    service: RegimeService = request.app.state.regime_service
    # Fitting (on cache miss/staleness) is CPU-bound — offload so it doesn't block the event loop.
    state, recommendation = await asyncio.to_thread(
        _detect, service, body.symbol, body.timeframe, df
    )

    return {
        "symbol": body.symbol,
        "timeframe": body.timeframe,
        "regime": state.regime.value,
        "probability": state.probability,
        "duration_bars": state.duration,
        "all_probabilities": {k.value: v for k, v in state.all_probabilities.items()},
        "recommendation": {
            "position_size_multiplier": recommendation.position_size_multiplier,
            "preferred_strategies": recommendation.preferred_strategies,
            "avoid_strategies": recommendation.avoid_strategies,
            "stop_loss_multiplier": recommendation.stop_loss_multiplier,
            "take_profit_multiplier": recommendation.take_profit_multiplier,
            "max_positions": recommendation.max_positions,
            "notes": recommendation.notes,
        },
    }
