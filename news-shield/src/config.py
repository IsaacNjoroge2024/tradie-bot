import logging

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

logger = logging.getLogger(__name__)


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env")

    # API Keys
    finnhub_api_key: str = ""
    alpha_vantage_api_key: str = ""

    # Redis
    redis_host: str = "localhost"
    redis_port: int = 6379

    # News Shield Thresholds
    sentiment_danger_threshold: float = -0.5
    sentiment_caution_threshold: float = -0.3

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
        return self


settings = Settings()
