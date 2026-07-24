import asyncio
import logging
from dataclasses import asdict
from datetime import date
from typing import Any, List, Optional

import pandas as pd
from fastapi import APIRouter, HTTPException, Query, Request
from fastapi.responses import HTMLResponse
from pydantic import BaseModel, Field

from ..analysis.metrics import (
    BacktestMetrics,
    calculate_metrics,
    monte_carlo_simulation,
    run_pandas_backtest,
)
from ..analysis.reports import BacktestReportGenerator
from ..config import settings
from ..data.loader import HistoricalDataLoader
from ..engine.backtrader_engine import BacktraderEngine, BacktraderEngineError
from ..engine.vectorbt_engine import VectorBTEngine
from ..monte_carlo.models import MonteCarloResult, SimulationConfig, Trade
from ..monte_carlo.simulator import MIN_TRADES_REQUIRED, MonteCarloSimulator
from ..optimization.grid_search import GridSearchOptimizer
from ..optimization.walk_forward import WalkForwardAnalyzer
from ..strategies.confluence_strategy import ConfluenceStrategy
from ..strategies.fvg_strategy import FVGStrategy

# MonteCarloSimulator (permutation-shuffle engine, Ticket 22) requires >=
# MIN_TRADES_REQUIRED trades for a statistically meaningful result. Below that,
# /backtest/monte-carlo falls back to the bootstrap-resampling
# monte_carlo_simulation() from Ticket 18, which has no minimum trade count.
# The two use different resampling strategies (permutation without replacement
# vs. bootstrap with replacement) and are not numerically equivalent — this is
# an intentional compatibility fallback for short backtests, not an oversight.

logger = logging.getLogger(__name__)
router = APIRouter()

_METRIC_FIELDS: frozenset[str] = frozenset(BacktestMetrics.__dataclass_fields__)

_STRATEGY_REGISTRY: dict = {
    "FVG_Strategy": FVGStrategy,
    "Confluence_Strategy": ConfluenceStrategy,
}


# ---------------------------------------------------------------------------
# Request / Response models
# ---------------------------------------------------------------------------


class BacktestRequest(BaseModel):
    symbol: str
    timeframe: str = "1D"
    start_date: date
    end_date: date
    exchange: str | None = None
    strategy: str = "FVG_Strategy"
    strategy_params: dict[str, Any] = Field(default_factory=dict)
    initial_cash: float = Field(default=100000.0, gt=0)
    commission: float = Field(default=settings.default_commission, ge=0)
    slippage: float = Field(default=settings.default_slippage, ge=0)
    engine: str = Field(default=settings.backtesting_engine)


class MetricsSchema(BaseModel):
    total_return: float
    annualized_return: float
    sharpe_ratio: float
    sortino_ratio: float
    max_drawdown: float
    max_drawdown_duration: int
    win_rate: float
    profit_factor: float
    avg_win: float
    avg_loss: float
    largest_win: float
    largest_loss: float
    total_trades: int
    avg_trade_duration: float
    expectancy: float

    @classmethod
    def from_dataclass(cls, m: BacktestMetrics) -> "MetricsSchema":
        return cls(**asdict(m))


class BacktestResponse(BaseModel):
    strategy: str
    symbol: str
    timeframe: str
    start_date: date
    end_date: date
    engine_used: str
    metrics: MetricsSchema
    equity_curve: List[float]
    timestamps: List[str]


class OptimizationRequest(BaseModel):
    symbol: str
    timeframe: str = "1D"
    start_date: date
    end_date: date
    exchange: str | None = None
    strategy: str = "FVG_Strategy"
    param_grid: dict[str, list]
    initial_cash: float = Field(default=100000.0, gt=0)
    commission: float = Field(default=settings.default_commission, ge=0)
    optimize_metric: str = "sharpe_ratio"


class OptimizationResponse(BaseModel):
    strategy: str
    symbol: str
    best_params: dict[str, Any]
    best_metric_value: float
    optimize_metric: str
    all_results: List[dict[str, Any]]


class WalkForwardRequest(BaseModel):
    symbol: str
    timeframe: str = "1D"
    start_date: date
    end_date: date
    exchange: str | None = None
    strategy: str = "FVG_Strategy"
    param_grid: dict[str, list]
    in_sample_pct: float = Field(default=settings.walk_forward_in_sample_pct, gt=0, lt=1)
    num_periods: int = Field(default=settings.walk_forward_num_periods, ge=2)
    initial_cash: float = Field(default=100000.0, gt=0)
    commission: float = Field(default=settings.default_commission, ge=0)


class WalkForwardResponse(BaseModel):
    strategy: str
    symbol: str
    combined_return: float
    consistency: float
    period_results: List[dict[str, Any]]


class MonteCarloRequest(BaseModel):
    symbol: str
    timeframe: str = "1D"
    start_date: date
    end_date: date
    exchange: str | None = None
    strategy: str = "FVG_Strategy"
    strategy_params: dict[str, Any] = Field(default_factory=dict)
    initial_cash: float = Field(default=100000.0, gt=0)
    commission: float = Field(default=settings.default_commission, ge=0)
    num_simulations: int = Field(default=settings.monte_carlo_simulations, ge=100, le=10000)


class MonteCarloResponse(BaseModel):
    strategy: str
    symbol: str
    median_return: float
    drawdown_95th: float
    risk_of_ruin: float
    simulations: int


class ComparisonResponse(BaseModel):
    symbol: str
    timeframe: str
    start_date: date
    end_date: date
    strategies: List[dict[str, Any]]
    best_strategy: str
    comparison_metric: str


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _resolve_strategy_class(name: str):
    cls = _STRATEGY_REGISTRY.get(name)
    if cls is None:
        raise HTTPException(
            status_code=400,
            detail=f"Unknown strategy '{name}'. Available: {list(_STRATEGY_REGISTRY)}",
        )
    return cls


def _build_strategy(strategy_class, params: dict[str, Any]):
    try:
        return strategy_class(**params)
    except (TypeError, ValueError) as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


async def _load_data(
    request: Request,
    symbol: str,
    timeframe: str,
    start_date: date,
    end_date: date,
    exchange: str | None = None,
) -> pd.DataFrame:
    loader: HistoricalDataLoader = request.app.state.data_loader
    try:
        data = await loader.load(symbol, timeframe, start_date, end_date, exchange)
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc))
    if data.empty:
        raise HTTPException(
            status_code=404,
            detail=f"No OHLCV data for {symbol}/{timeframe} between {start_date} and {end_date}",
        )
    return data


def _trades_df_to_objects(
    trades: pd.DataFrame, symbol: str, strategy: str, initial_cash: float
) -> list[Trade]:
    """Convert a run_pandas_backtest() trades DataFrame into monte_carlo.Trade objects."""
    trade_objs: list[Trade] = []
    for i, t in enumerate(trades.to_dict("records")):
        pnl_val = float(t.get("pnl", 0.0))
        trade_objs.append(
            Trade(
                trade_id=f"T{i}",
                symbol=symbol,
                side=str(t.get("side", "BUY")).upper(),
                entry_price=float(t.get("entry_price", 0.0)),
                exit_price=float(t.get("exit_price", 0.0)),
                quantity=1.0,
                pnl=pnl_val,
                pnl_pct=pnl_val / initial_cash if initial_cash > 0 else 0.0,
                hold_time_minutes=0,
                strategy=strategy,
                timestamp=str(t.get("entry_time", "")),
            )
        )
    return trade_objs


async def _run_new_monte_carlo(
    trades: pd.DataFrame,
    symbol: str,
    strategy: str,
    initial_cash: float,
    num_simulations: int,
    ruin_threshold_pct: float = 50.0,
) -> Optional[MonteCarloResult]:
    """Run the Ticket 22 permutation-based simulator, or None if too few trades."""
    if len(trades) < MIN_TRADES_REQUIRED:
        return None

    trade_objs = _trades_df_to_objects(trades, symbol, strategy, initial_cash)
    config = SimulationConfig(
        initial_balance=initial_cash,
        num_simulations=num_simulations,
        ruin_threshold_pct=ruin_threshold_pct,
    )
    # CPU-bound (joblib dispatch + wait); offload so this doesn't block the event loop.
    return await asyncio.to_thread(MonteCarloSimulator(config).run, trade_objs)


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------


@router.post("/backtest/run", response_model=BacktestResponse)
async def run_backtest(req: BacktestRequest, request: Request) -> BacktestResponse:
    """Run a full backtest for a strategy over the specified period."""
    strategy_class = _resolve_strategy_class(req.strategy)
    data = await _load_data(
        request, req.symbol, req.timeframe, req.start_date, req.end_date, req.exchange
    )

    strategy = _build_strategy(strategy_class, req.strategy_params)
    signals = strategy.generate_signals(data)

    if req.engine == "vectorbt":
        vbt_engine = VectorBTEngine()
        result = vbt_engine.run_backtest(
            data, signals, req.initial_cash, req.commission, req.slippage, req.timeframe
        )
        metrics = calculate_metrics(
            result.equity_curve, result.trades, req.initial_cash, req.timeframe
        )
        equity_curve = result.equity_curve
        engine_used = "vectorbt" if _vectorbt_available() else "pandas_fallback"
    elif req.engine == "backtrader":
        try:
            bt_engine = BacktraderEngine()
            result = bt_engine.run_backtest(
                data, signals, req.initial_cash, req.commission, req.slippage
            )
        except BacktraderEngineError as exc:
            raise HTTPException(status_code=503, detail=str(exc)) from exc
        metrics = calculate_metrics(
            result.equity_curve, result.trades, req.initial_cash, req.timeframe
        )
        equity_curve = result.equity_curve
        engine_used = "backtrader"
    elif req.engine == "pandas":
        equity_curve, trades = run_pandas_backtest(
            data, signals, req.initial_cash, req.commission + req.slippage
        )
        metrics = calculate_metrics(equity_curve, trades, req.initial_cash, req.timeframe)
        engine_used = "pandas"
    else:
        raise HTTPException(
            status_code=422,
            detail=f"Unknown engine '{req.engine}'. Must be one of: pandas, vectorbt, backtrader",
        )

    return BacktestResponse(
        strategy=strategy.name,
        symbol=req.symbol,
        timeframe=req.timeframe,
        start_date=req.start_date,
        end_date=req.end_date,
        engine_used=engine_used,
        metrics=MetricsSchema.from_dataclass(metrics),
        equity_curve=equity_curve.tolist(),
        timestamps=[str(ts) for ts in equity_curve.index],
    )


@router.post("/backtest/optimize", response_model=OptimizationResponse)
async def optimize_strategy(req: OptimizationRequest, request: Request) -> OptimizationResponse:
    """Exhaustive grid search to find the best strategy parameters."""
    strategy_class = _resolve_strategy_class(req.strategy)
    data = await _load_data(
        request, req.symbol, req.timeframe, req.start_date, req.end_date, req.exchange
    )

    optimizer = GridSearchOptimizer()
    try:
        result = optimizer.optimize(
            strategy_class,
            data,
            req.param_grid,
            req.initial_cash,
            req.commission,
            req.optimize_metric,
            req.timeframe,
        )
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc))

    return OptimizationResponse(
        strategy=req.strategy,
        symbol=req.symbol,
        best_params=result.best_params,
        best_metric_value=result.best_metric_value,
        optimize_metric=result.optimize_metric,
        all_results=result.all_results,
    )


@router.post("/backtest/walk-forward", response_model=WalkForwardResponse)
async def walk_forward(req: WalkForwardRequest, request: Request) -> WalkForwardResponse:
    """Run walk-forward analysis to validate strategy robustness out-of-sample."""
    strategy_class = _resolve_strategy_class(req.strategy)
    data = await _load_data(
        request, req.symbol, req.timeframe, req.start_date, req.end_date, req.exchange
    )

    analyzer = WalkForwardAnalyzer(
        in_sample_pct=req.in_sample_pct,
        num_periods=req.num_periods,
    )
    try:
        result = analyzer.run(
            strategy_class,
            data,
            req.param_grid,
            req.initial_cash,
            req.commission,
            req.timeframe,
        )
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc))

    return WalkForwardResponse(
        strategy=req.strategy,
        symbol=req.symbol,
        combined_return=result.combined_return,
        consistency=result.consistency,
        period_results=result.period_results,
    )


@router.get("/backtest/compare", response_model=ComparisonResponse)
async def compare_strategies(
    request: Request,
    strategies: List[str] = Query(..., description="Strategy names to compare"),
    symbol: str = Query(...),
    timeframe: str = Query(default="1D"),
    start_date: date = Query(...),
    end_date: date = Query(...),
    exchange: str | None = Query(default=None),
    initial_cash: float = Query(default=100000.0),
    commission: float = Query(default=settings.default_commission),
    comparison_metric: str = Query(default="sharpe_ratio"),
) -> ComparisonResponse:
    """Compare multiple strategies over the same period."""
    if comparison_metric not in _METRIC_FIELDS:
        raise HTTPException(
            status_code=422,
            detail=f"Unknown comparison_metric '{comparison_metric}'. "
            f"Valid metrics: {sorted(_METRIC_FIELDS)}",
        )

    data = await _load_data(request, symbol, timeframe, start_date, end_date, exchange)

    strategy_results: list[dict[str, Any]] = []

    for strategy_name in strategies:
        strategy_class = _resolve_strategy_class(strategy_name)
        try:
            strategy = strategy_class()
            signals = strategy.generate_signals(data)
            equity_curve, trades = run_pandas_backtest(data, signals, initial_cash, commission)
            metrics = calculate_metrics(equity_curve, trades, initial_cash, timeframe)
            strategy_results.append(
                {
                    "strategy": strategy_name,
                    "metrics": asdict(metrics),
                    "comparison_value": float(getattr(metrics, comparison_metric)),
                }
            )
        except Exception as exc:
            logger.warning(f"Failed to evaluate {strategy_name}: {exc}")
            strategy_results.append(
                {
                    "strategy": strategy_name,
                    "error": str(exc),
                    "comparison_value": float("-inf"),
                }
            )

    successful_results = [r for r in strategy_results if "error" not in r]
    if not successful_results:
        raise HTTPException(status_code=422, detail="All strategy evaluations failed")

    best = max(successful_results, key=lambda r: r["comparison_value"])

    return ComparisonResponse(
        symbol=symbol,
        timeframe=timeframe,
        start_date=start_date,
        end_date=end_date,
        strategies=strategy_results,
        best_strategy=best["strategy"],
        comparison_metric=comparison_metric,
    )


@router.post("/backtest/report", response_class=HTMLResponse)
async def generate_report(req: BacktestRequest, request: Request) -> HTMLResponse:
    """Run a backtest and return a full HTML report with equity curve, trade log,
    and (when at least 30 trades are available) a Monte Carlo risk analysis section."""
    strategy_class = _resolve_strategy_class(req.strategy)
    data = await _load_data(
        request, req.symbol, req.timeframe, req.start_date, req.end_date, req.exchange
    )

    strategy = _build_strategy(strategy_class, req.strategy_params)
    signals = strategy.generate_signals(data)
    equity_curve, trades = run_pandas_backtest(
        data, signals, req.initial_cash, req.commission + req.slippage
    )
    metrics = calculate_metrics(equity_curve, trades, req.initial_cash, req.timeframe)

    monte_carlo_result = await _run_new_monte_carlo(
        trades,
        symbol=req.symbol,
        strategy=strategy.name,
        initial_cash=req.initial_cash,
        num_simulations=settings.monte_carlo_simulations,
    )

    generator = BacktestReportGenerator()
    html = generator.generate(
        strategy=strategy.name,
        symbol=req.symbol,
        timeframe=req.timeframe,
        start_date=req.start_date,
        end_date=req.end_date,
        metrics=metrics,
        equity_curve=equity_curve,
        trades=trades,
        monte_carlo=monte_carlo_result,
    )

    return HTMLResponse(content=html)


@router.post("/backtest/monte-carlo", response_model=MonteCarloResponse)
async def run_monte_carlo(req: MonteCarloRequest, request: Request) -> MonteCarloResponse:
    """Run Monte Carlo simulation on backtest trades to estimate realistic expectations."""
    strategy_class = _resolve_strategy_class(req.strategy)
    data = await _load_data(
        request, req.symbol, req.timeframe, req.start_date, req.end_date, req.exchange
    )

    strategy = _build_strategy(strategy_class, req.strategy_params)
    signals = strategy.generate_signals(data)
    _, trades = run_pandas_backtest(data, signals, req.initial_cash, req.commission)

    result = await _run_new_monte_carlo(
        trades,
        symbol=req.symbol,
        strategy=req.strategy,
        initial_cash=req.initial_cash,
        num_simulations=req.num_simulations,
    )

    if result is not None:
        median_ret = (result.median_final_balance - req.initial_cash) / req.initial_cash
        return MonteCarloResponse(
            strategy=req.strategy,
            symbol=req.symbol,
            median_return=round(median_ret, 6),
            drawdown_95th=round(result.percentile_95_max_drawdown / 100.0, 6),
            risk_of_ruin=round(result.probability_of_ruin, 4),
            simulations=result.config.num_simulations,
        )
    else:
        # Same offload rationale as the branch above — keep this handler's blocking
        # behavior consistent regardless of which Monte Carlo path it takes.
        mc_result = await asyncio.to_thread(
            monte_carlo_simulation,
            trades,
            initial_cash=req.initial_cash,
            num_simulations=req.num_simulations,
            confidence_level=settings.monte_carlo_confidence_level,
        )

        return MonteCarloResponse(
            strategy=req.strategy,
            symbol=req.symbol,
            median_return=mc_result.median_return,
            drawdown_95th=mc_result.drawdown_95th,
            risk_of_ruin=mc_result.risk_of_ruin,
            simulations=mc_result.simulations,
        )


def _vectorbt_available() -> bool:
    try:
        import vectorbt  # noqa: F401

        return True
    except ImportError:
        return False
