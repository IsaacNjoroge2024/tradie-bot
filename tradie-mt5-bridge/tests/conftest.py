from unittest.mock import patch

import pytest


@pytest.fixture(autouse=True)
def default_auth_dev_mode():
    """Patch auth settings to dev mode for every test.

    Without this, tests run on CI (no .env file) would have mt5_dev_mode=False, causing
    verify_api_key to fail closed and return 403 for all /api/* route tests.
    Auth-specific tests override this with their own inner patch("app.auth.settings") block.
    """
    with patch("app.auth.settings") as mock_settings:
        mock_settings.mt5_api_key = ""
        mock_settings.mt5_dev_mode = True
        yield mock_settings
