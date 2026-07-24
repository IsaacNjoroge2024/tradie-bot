import copy
from typing import ClassVar, Dict, List

from .models import MonteCarloResult, SimulationConfig, Trade
from .simulator import MIN_TRADES_REQUIRED, MonteCarloSimulator


class StressTestSuite:
    """
    Run multiple stress test scenarios on a strategy.
    """

    # ClassVar: a shared, read-only lookup table — never mutated per-instance.
    SCENARIOS: ClassVar[dict] = {
        "baseline": {"skip_trade_pct": 0.0, "description": "Normal conditions"},
        "miss_10_pct": {
            "skip_trade_pct": 10.0,
            "description": "Miss 10% of trades (slippage, connectivity)",
        },
        "miss_20_pct": {
            "skip_trade_pct": 20.0,
            "description": "Miss 20% of trades (severe conditions)",
        },
        "miss_best_10_pct": {
            "skip_best": True,
            "skip_pct": 10.0,
            "description": "Miss 10% best trades",
        },
        "double_losses": {"loss_multiplier": 2.0, "description": "Double all losing trades"},
        "halve_wins": {"win_multiplier": 0.5, "description": "Halve all winning trades"},
    }

    def run_all_scenarios(
        self, trades: List[Trade], base_config: SimulationConfig
    ) -> Dict[str, MonteCarloResult]:
        """Run all stress test scenarios"""

        results = {}

        for scenario_name, scenario_params in self.SCENARIOS.items():
            modified_trades = self._apply_scenario(trades, scenario_params)
            modified_config = self._modify_config(base_config, scenario_params)

            simulator = MonteCarloSimulator(modified_config)
            result = simulator.run(modified_trades)
            results[scenario_name] = result

        return results

    def _apply_scenario(self, trades: List[Trade], params: dict) -> List[Trade]:
        """Apply scenario modifications to trades"""

        # Deep copy trades so modifications don't leak across scenarios
        modified = [copy.deepcopy(t) for t in trades]

        if params.get("skip_best"):
            # Sort by P&L and remove top performers. Clamp so the reduced list
            # never drops below MonteCarloSimulator's hard minimum — with an
            # input trade count close to that floor (e.g. 31 trades), removing
            # the nominal 10% would otherwise leave too few trades to simulate
            # and raise ValueError, taking down the whole scenario batch.
            sorted_trades = sorted(modified, key=lambda t: t.pnl, reverse=True)
            nominal_skip = int(len(sorted_trades) * params["skip_pct"] / 100)
            max_skip = max(0, len(sorted_trades) - MIN_TRADES_REQUIRED)
            n_skip = min(nominal_skip, max_skip)
            modified = sorted_trades[n_skip:]

        if params.get("loss_multiplier"):
            multiplier = params["loss_multiplier"]
            for trade in modified:
                if trade.pnl < 0:
                    trade.pnl *= multiplier
                    trade.pnl_pct *= multiplier

        if params.get("win_multiplier"):
            multiplier = params["win_multiplier"]
            for trade in modified:
                if trade.pnl > 0:
                    trade.pnl *= multiplier
                    trade.pnl_pct *= multiplier

        return modified

    def _modify_config(self, config: SimulationConfig, params: dict) -> SimulationConfig:
        """Modify config based on scenario"""

        new_config = SimulationConfig(
            initial_balance=config.initial_balance,
            num_simulations=config.num_simulations,
            ruin_threshold_pct=config.ruin_threshold_pct,
            skip_trade_pct=params.get("skip_trade_pct", config.skip_trade_pct),
            position_sizing=config.position_sizing,
            risk_per_trade_pct=config.risk_per_trade_pct,
            random_seed=config.random_seed,
        )

        return new_config
