import plotly.graph_objects as go
from plotly.subplots import make_subplots

from .models import MonteCarloResult


class MonteCarloVisualizer:
    """Generate interactive visualizations for Monte Carlo results"""

    def create_report(self, result: MonteCarloResult) -> go.Figure:
        """Create comprehensive Monte Carlo report visualization"""

        fig = make_subplots(
            rows=3,
            cols=2,
            subplot_titles=(
                "Equity Curve Distribution",
                "Final Balance Percentiles",
                "Drawdown Distribution",
                "Risk Metrics Summary",
                "Streak Analysis",
                "Recommendation",
            ),
            specs=[
                [{"type": "scatter"}, {"type": "bar"}],
                [{"type": "bar"}, {"type": "bar"}],
                [{"type": "bar"}, {"type": "indicator"}],
            ],
        )

        # 1. Equity Curves (best, worst, median, confidence bands)
        x = list(range(len(result.median_equity_curve)))

        fig.add_trace(
            go.Scatter(
                x=x,
                y=result.percentile_95_curve,
                name="95th Percentile",
                line=dict(color="green", dash="dash"),
            ),
            row=1,
            col=1,
        )
        fig.add_trace(
            go.Scatter(
                x=x,
                y=result.median_equity_curve,
                name="Median",
                line=dict(color="blue", width=2),
            ),
            row=1,
            col=1,
        )
        fig.add_trace(
            go.Scatter(
                x=x,
                y=result.percentile_5_curve,
                name="5th Percentile",
                line=dict(color="red", dash="dash"),
            ),
            row=1,
            col=1,
        )
        fig.add_trace(
            go.Scatter(
                x=x,
                y=result.worst_equity_curve,
                name="Worst Case",
                line=dict(color="darkred", width=1),
            ),
            row=1,
            col=1,
        )

        # 2. Final Balance Percentiles Bar Chart
        balance_metrics = {
            "Min": result.min_final_balance,
            "5th %": result.percentile_5_balance,
            "25th %": result.percentile_25_balance,
            "Median": result.median_final_balance,
            "75th %": result.percentile_75_balance,
            "95th %": result.percentile_95_balance,
            "Max": result.max_final_balance,
        }
        fig.add_trace(
            go.Bar(
                x=list(balance_metrics.keys()),
                y=list(balance_metrics.values()),
                name="Final Balance",
                marker_color="royalblue",
            ),
            row=1,
            col=2,
        )

        # 3. Drawdown Percentiles Bar Chart
        dd_metrics = {
            "Median DD": result.median_max_drawdown,
            "Mean DD": result.mean_max_drawdown,
            "95th % DD": result.percentile_95_max_drawdown,
            "99th % DD": result.percentile_99_max_drawdown,
        }
        fig.add_trace(
            go.Bar(
                x=list(dd_metrics.keys()),
                y=list(dd_metrics.values()),
                name="Drawdown %",
                marker_color="coral",
            ),
            row=2,
            col=1,
        )

        # 4. Risk Metrics Summary Bar Chart
        metrics = {
            "P(Profit) %": result.probability_of_profit * 100,
            "P(Ruin) %": result.probability_of_ruin * 100,
            "VaR 95% ($)": result.var_95,
            "CVaR 95% ($)": result.cvar_95,
        }

        fig.add_trace(
            go.Bar(
                x=list(metrics.keys()),
                y=list(metrics.values()),
                name="Risk Metrics",
                marker_color="purple",
            ),
            row=2,
            col=2,
        )

        # 5. Losing Streak Summary Bar Chart
        streak_metrics = {
            "Worst Streak": result.worst_losing_streak,
            "Median Streak": result.median_losing_streak,
        }
        fig.add_trace(
            go.Bar(
                x=list(streak_metrics.keys()),
                y=list(streak_metrics.values()),
                name="Losing Streaks",
                marker_color="darkorange",
            ),
            row=3,
            col=1,
        )

        # 6. Recommendation Indicator
        rec_colors = {"APPROVED": "green", "CAUTION": "orange", "REJECTED": "red"}
        color = rec_colors.get(result.recommendation, "gray")

        fig.add_trace(
            go.Indicator(
                mode="number+delta",
                value=result.probability_of_profit * 100,
                title={"text": f"Recommendation: {result.recommendation}"},
                delta={"reference": 60, "relative": False},
                domain={"x": [0, 1], "y": [0, 1]},
                number={"suffix": "%", "font": {"color": color}},
            ),
            row=3,
            col=2,
        )

        fig.update_layout(
            height=900,
            title_text=f"Monte Carlo Analysis ({result.config.num_simulations:,} simulations, {result.num_trades} trades)",
            showlegend=True,
        )

        return fig

    def create_equity_cone(self, result: MonteCarloResult) -> go.Figure:
        """Create equity curve cone chart"""

        x = list(range(len(result.median_equity_curve)))

        fig = go.Figure()

        # Fill between 5th and 95th percentile
        fig.add_trace(
            go.Scatter(
                x=x + x[::-1],
                y=result.percentile_95_curve + result.percentile_5_curve[::-1],
                fill="toself",
                fillcolor="rgba(0,100,80,0.2)",
                line=dict(color="rgba(255,255,255,0)"),
                name="90% Confidence Interval",
            )
        )

        # Median line
        fig.add_trace(
            go.Scatter(
                x=x,
                y=result.median_equity_curve,
                line=dict(color="blue", width=2),
                name="Median Outcome",
            )
        )

        # Initial balance reference
        fig.add_hline(
            y=result.config.initial_balance,
            line_dash="dash",
            line_color="gray",
            annotation_text="Initial Balance",
        )

        fig.update_layout(
            title="Equity Curve Confidence Cone",
            xaxis_title="Trade Number",
            yaxis_title="Account Balance ($)",
            height=500,
        )

        return fig
