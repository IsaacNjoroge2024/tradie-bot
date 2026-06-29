import logging
from pathlib import Path

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

logger = logging.getLogger(__name__)

# Resolve .env from the project root regardless of working directory.
# __file__ = backtesting-service/src/config.py → parents[2] = tradie-bot/
_ENV_FILE = Path(__file__).resolve().parents[2] / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=str(_ENV_FILE), env_file_encoding="utf-8", extra="ignore"
    )

    # TimescaleDB
    db_host: str = "localhost"
    db_port: int = 5433
    db_name: str = "tradie"
    db_user: str = "tradie"
    db_password: str = ""

    # Backtesting engine
    backtesting_engine: str = "vectorbt"
    default_commission: float = 0.001
    default_slippage: float = 0.0005
    default_initial_cash: float = 100000.0

    # Walk-forward analysis
    walk_forward_in_sample_pct: float = 0.7
    walk_forward_num_periods: int = 6

    # Monte Carlo simulation
    monte_carlo_simulations: int = 1000
    monte_carlo_confidence_level: float = 95.0

    # CORS
    cors_allowed_origins: list[str] = ["http://localhost:8080", "http://localhost:3000"]

    @model_validator(mode="after")
    def warn_on_missing_config(self) -> "Settings":
        if not self.db_password:
            logger.warning(
                "DB_PASSWORD is not configured; TimescaleDB connection will fail unless "
                "running with no-auth (development only)"
            )
        return self


settings = Settings()
