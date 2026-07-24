import json
import click
from ..monte_carlo.models import SimulationConfig, Trade
from ..monte_carlo.simulator import MonteCarloSimulator
from ..monte_carlo.thresholds import THRESHOLDS


def _load_trade(t: dict, idx: int) -> Trade:
    return Trade(
        trade_id=str(t.get("trade_id", f"T{idx}")),
        symbol=str(t.get("symbol", "UNKNOWN")),
        side=str(t.get("side", t.get("type", "BUY"))).upper(),
        entry_price=float(t.get("entry_price", 0.0)),
        exit_price=float(t.get("exit_price", 0.0)),
        quantity=float(t.get("quantity", t.get("size", 1.0))),
        pnl=float(t.get("pnl", 0.0)),
        pnl_pct=float(t.get("pnl_pct", t.get("return_pct", 0.0))),
        hold_time_minutes=int(t.get("hold_time_minutes", t.get("duration_minutes", 0))),
        strategy=str(t.get("strategy", "CLI")),
        timestamp=str(t.get("timestamp", t.get("entry_time", ""))),
    )


@click.command()
@click.argument("trades_file", type=click.Path(exists=True))
@click.option(
    "--simulations", "-n", default=THRESHOLDS.default_num_simulations, help="Number of simulations"
)
@click.option("--initial-balance", "-b", default=10000.0, help="Initial balance")
@click.option(
    "--ruin-threshold",
    "-r",
    default=THRESHOLDS.default_ruin_threshold_pct,
    help="Ruin threshold %",
)
@click.option("--output", "-o", default=None, help="Output file for results")
def monte_carlo(trades_file, simulations, initial_balance, ruin_threshold, output):
    """Run Monte Carlo simulation on trade history"""

    with open(trades_file) as f:
        trades_data = json.load(f)

    trades = [_load_trade(t, i) for i, t in enumerate(trades_data)]

    config = SimulationConfig(
        initial_balance=initial_balance,
        num_simulations=simulations,
        ruin_threshold_pct=ruin_threshold,
    )

    click.echo(f"Running {simulations:,} simulations on {len(trades)} trades...")

    simulator = MonteCarloSimulator(config)
    result = simulator.run(trades)

    click.echo(f"\n{'='*50}")
    click.echo(f"RECOMMENDATION: {result.recommendation}")
    click.echo(f"{'='*50}")

    if result.recommendation_reasons:
        click.echo("\nReasons:")
        for reason in result.recommendation_reasons:
            click.echo(f"  ⚠️  {reason}")

    click.echo("\nKey Metrics:")
    click.echo(f"  Probability of Profit: {result.probability_of_profit:.1%}")
    click.echo(f"  Probability of Ruin:   {result.probability_of_ruin:.1%}")
    click.echo(f"  Median Final Balance:  ${result.median_final_balance:,.2f}")
    click.echo(f"  VaR 95%:               ${result.var_95:,.2f}")
    click.echo(f"  Max Drawdown (95%):    {result.percentile_95_max_drawdown:.1f}%")
    click.echo(f"  Worst Losing Streak:   {result.worst_losing_streak} trades")

    if output:
        res_dict = {
            "recommendation": result.recommendation,
            "recommendation_reasons": result.recommendation_reasons,
            "num_trades": result.num_trades,
            "probability_of_profit": result.probability_of_profit,
            "probability_of_ruin": result.probability_of_ruin,
            "median_final_balance": result.median_final_balance,
            "mean_final_balance": result.mean_final_balance,
            "std_final_balance": result.std_final_balance,
            "percentile_5_balance": result.percentile_5_balance,
            "percentile_25_balance": result.percentile_25_balance,
            "percentile_75_balance": result.percentile_75_balance,
            "percentile_95_balance": result.percentile_95_balance,
            "min_final_balance": result.min_final_balance,
            "max_final_balance": result.max_final_balance,
            "median_max_drawdown": result.median_max_drawdown,
            "mean_max_drawdown": result.mean_max_drawdown,
            "percentile_95_max_drawdown": result.percentile_95_max_drawdown,
            "percentile_99_max_drawdown": result.percentile_99_max_drawdown,
            "var_95": result.var_95,
            "var_99": result.var_99,
            "cvar_95": result.cvar_95,
            "expected_sharpe": result.expected_sharpe,
            "worst_losing_streak": result.worst_losing_streak,
            "median_losing_streak": result.median_losing_streak,
        }
        with open(output, "w") as f:
            json.dump(res_dict, f, indent=2, default=str)
        click.echo(f"\nResults saved to {output}")


if __name__ == "__main__":
    monte_carlo()
