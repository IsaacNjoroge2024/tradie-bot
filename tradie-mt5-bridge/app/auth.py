from fastapi import HTTPException, Security
from fastapi.security import APIKeyHeader

from .config import settings

_api_key_header = APIKeyHeader(name="X-API-Key", auto_error=False)


async def verify_api_key(api_key: str | None = Security(_api_key_header)) -> None:
    """Validate X-API-Key header. When MT5_API_KEY is unset the service is open (dev mode)."""
    configured = settings.mt5_api_key
    if not configured:
        return
    if api_key != configured:
        raise HTTPException(status_code=403, detail="Forbidden: invalid or missing API key")
