"""CLI Package for Backtesting Service.

Note: intentionally does not eagerly import monte_carlo_cli here — the CLI is
invoked as `python -m src.cli.monte_carlo_cli`, and importing it a second time
via this package __init__ triggers a "module found in sys.modules" RuntimeWarning.
"""
