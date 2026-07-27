import logging
from pathlib import Path
from typing import Literal

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

logger = logging.getLogger(__name__)

# Resolve .env from the project root regardless of working directory.
# __file__ = news-shield/src/config.py → parents[2] = tradie-bot/
_ENV_FILE = Path(__file__).resolve().parents[2] / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=str(_ENV_FILE), env_file_encoding="utf-8", extra="ignore"
    )

    # API Keys
    finnhub_api_key: str = ""
    alpha_vantage_api_key: str = ""

    # Redis
    redis_host: str = "localhost"
    redis_port: int = 6379

    # News Shield Thresholds
    sentiment_danger_threshold: float = -0.5
    sentiment_caution_threshold: float = -0.3

    # Sentiment Analysis Engine (Ticket 23 — FinBERT)
    sentiment_primary_analyzer: Literal["finbert", "vader"] = "finbert"
    finbert_device: Literal["auto", "cuda", "cpu"] = "auto"
    finbert_max_length: int = Field(default=512, ge=1, le=512)  # 512 = BERT's position limit
    finbert_batch_size: int = Field(default=16, ge=1)

    # High Impact Events
    high_impact_pause_minutes: int = 30

    # VIX Thresholds
    vix_elevated: float = 25.0
    vix_extreme: float = 35.0

    # CORS — restrict to known internal origins in production
    cors_allowed_origins: list[str] = ["http://localhost:8080", "http://localhost:3000"]

    @model_validator(mode="after")
    def warn_on_missing_keys(self) -> "Settings":
        if not self.finnhub_api_key:
            logger.warning(
                "FINNHUB_API_KEY is not configured; "
                "event/news calls will return empty results and market status will be uninformed"
            )
        if not self.alpha_vantage_api_key:
            logger.warning(
                "ALPHA_VANTAGE_API_KEY is not configured; "
                "Alpha Vantage fallback news source will be unavailable"
            )
        return self


settings = Settings()
