from fastapi import HTTPException, Security
from fastapi.security import APIKeyHeader

from .config import settings

_api_key_header = APIKeyHeader(name="X-API-Key", auto_error=False)


async def verify_api_key(api_key: str | None = Security(_api_key_header)) -> None:
    """Validate X-API-Key header.

    Fails closed (403) when MT5_API_KEY is unset unless MT5_DEV_MODE=true is explicitly set.
    """
    configured = settings.mt5_api_key
    if not configured:
        if settings.mt5_dev_mode:
            return
        raise HTTPException(
            status_code=403,
            detail="Forbidden: MT5_API_KEY is not configured; set MT5_DEV_MODE=true for dev access",
        )
    if api_key != configured:
        raise HTTPException(status_code=403, detail="Forbidden: invalid or missing API key")
