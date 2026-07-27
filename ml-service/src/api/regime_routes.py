import asyncio

import pandas as pd
from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel, model_validator

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

    @model_validator(mode="after")
    def _check_equal_lengths(self) -> "PriceData":
        lengths = {
            len(self.timestamp),
            len(self.open),
            len(self.high),
            len(self.low),
            len(self.close),
            len(self.volume),
        }
        if len(lengths) > 1:
            raise ValueError("All price_data fields must have the same length")
        return self


class RegimeRequest(BaseModel):
    symbol: str
    timeframe: str
    price_data: PriceData


class MultiTimeframeRequest(BaseModel):
    symbol: str
    price_data_1h: PriceData
    price_data_4h: PriceData
    price_data_1d: PriceData


def _price_data_to_df(price_data: PriceData) -> pd.DataFrame:
    return pd.DataFrame(
        {
            "timestamp": price_data.timestamp,
            "open": price_data.open,
            "high": price_data.high,
            "low": price_data.low,
            "close": price_data.close,
            "volume": price_data.volume,
        }
    )


def _require_min_bars(df: pd.DataFrame, label: str) -> None:
    if len(df) < settings.regime_min_bars:
        raise HTTPException(400, f"Need at least {settings.regime_min_bars} bars for {label}")


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

    df = _price_data_to_df(body.price_data)
    _require_min_bars(df, "regime detection")

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


def _detect_multi_timeframe(
    service: RegimeService, symbol: str, dfs: dict[str, pd.DataFrame]
) -> dict:
    """Runs the 3-timeframe regime fit/predict + should-trade analysis (CPU-bound)."""
    regimes = service.get_multi_timeframe_regime(symbol, dfs["1H"], dfs["4H"], dfs["1D"])
    decision = service.should_trade(regimes)

    return {
        "symbol": symbol,
        "regimes": {
            tf: {
                "regime": state.regime.value,
                "probability": state.probability,
                "duration_bars": state.duration,
            }
            for tf, state in regimes.items()
        },
        "should_trade": decision["should_trade"],
        "reason": decision["reason"],
        "dominant_regime": decision["dominant_regime"],
        "alignment": decision["alignment"],
        "avg_confidence": decision["avg_confidence"],
    }


@router.post("/multi-timeframe")
async def detect_multi_timeframe_regime(request: Request, body: MultiTimeframeRequest):
    """Analyze regime alignment across 1H/4H/1D and recommend whether conditions favor trading."""

    dfs: dict[str, pd.DataFrame] = {}
    for label, price_data in (
        ("1H", body.price_data_1h),
        ("4H", body.price_data_4h),
        ("1D", body.price_data_1d),
    ):
        df = _price_data_to_df(price_data)
        _require_min_bars(df, f"the {label} timeframe")
        dfs[label] = df

    service: RegimeService = request.app.state.regime_service
    return await asyncio.to_thread(_detect_multi_timeframe, service, body.symbol, dfs)
