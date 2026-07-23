import logging
from pathlib import Path

from pydantic import SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

logger = logging.getLogger(__name__)

# Resolve .env from the project root regardless of working directory.
# __file__ = tradie-mt5-bridge/app/config.py → parents[2] = tradie-bot/
_ENV_FILE = Path(__file__).resolve().parents[2] / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=str(_ENV_FILE), env_file_encoding="utf-8", extra="ignore"
    )

    # MT5 Connection
    mt5_login: int = 0
    mt5_password: SecretStr = SecretStr("")
    mt5_server: str = ""
    mt5_path: str = "/root/.wine/drive_c/Program Files/MetaTrader 5/terminal64.exe"
    mt5_timeout: int = 60000
    mt5_magic: int = 123456

    # Retry
    mt5_max_retries: int = 3
    mt5_retry_delay_seconds: float = 1.0

    # Authentication
    mt5_api_key: str = ""
    mt5_dev_mode: bool = False

    # CORS
    cors_allowed_origins: list[str] = ["http://localhost:8080", "http://localhost:3000"]

    @model_validator(mode="after")
    def warn_on_missing_config(self) -> "Settings":
        if not self.mt5_login:
            logger.warning("MT5_LOGIN is not configured; MT5 connection will fail")
        if not self.mt5_password.get_secret_value():
            logger.warning("MT5_PASSWORD is not configured; MT5 connection will fail")
        if not self.mt5_server:
            logger.warning("MT5_SERVER is not configured; MT5 connection will fail")
        return self


settings = Settings()
