from pydantic_settings import BaseSettings, SettingsConfigDict


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


settings = Settings()
