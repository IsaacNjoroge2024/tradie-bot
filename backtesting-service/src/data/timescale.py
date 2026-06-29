import logging
from typing import Optional

import asyncpg

from ..config import settings

logger = logging.getLogger(__name__)


class TimescaleDBPool:
    """Manages an asyncpg connection pool for TimescaleDB."""

    def __init__(self) -> None:
        self._pool: Optional[asyncpg.Pool] = None

    async def initialize(self) -> None:
        try:
            self._pool = await asyncpg.create_pool(
                host=settings.db_host,
                port=settings.db_port,
                database=settings.db_name,
                user=settings.db_user,
                password=settings.db_password,
                min_size=2,
                max_size=10,
                command_timeout=60,
            )
            logger.info("TimescaleDB connection pool established")
        except Exception as e:
            logger.error(f"Failed to connect to TimescaleDB: {e}")
            self._pool = None

    async def close(self) -> None:
        if self._pool:
            await self._pool.close()
            self._pool = None
            logger.info("TimescaleDB connection pool closed")

    @property
    def pool(self) -> asyncpg.Pool:
        if self._pool is None:
            raise RuntimeError(
                "TimescaleDB pool is not initialized. "
                "Ensure the database is running and DB_* environment variables are set."
            )
        return self._pool

    @property
    def is_connected(self) -> bool:
        return self._pool is not None
