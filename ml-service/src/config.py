from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

# Resolve .env from the project root regardless of working directory.
# __file__ = ml-service/src/config.py → parents[2] = tradie-bot/
_ENV_FILE = Path(__file__).resolve().parents[2] / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=str(_ENV_FILE), env_file_encoding="utf-8", extra="ignore"
    )

    # Regime detection
    regime_min_bars: int = 50

    # CORS — restrict to known internal origins in production
    cors_allowed_origins: list[str] = ["http://localhost:8080", "http://localhost:3000"]


settings = Settings()
