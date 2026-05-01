import httpx
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from src.services.sentiment_analyzer import SentimentAnalyzer, SentimentResult


@pytest.fixture
async def analyzer():
    a = SentimentAnalyzer()
    await a.initialize()
    yield a
    await a.aclose()


class TestSentimentAnalyzerInit:
    @pytest.mark.asyncio
    async def test_initialize_loads_vader(self):
        a = SentimentAnalyzer()
        assert a.vader is None
        await a.initialize()
        assert a.vader is not None
        await a.aclose()

    @pytest.mark.asyncio
    async def test_financial_lexicon_applied(self, analyzer):
        assert "bullish" in analyzer.vader.lexicon
        assert "bearish" in analyzer.vader.lexicon
        assert analyzer.vader.lexicon["bullish"] == 2.0
        assert analyzer.vader.lexicon["bearish"] == -2.0


class TestAnalyzeSentiment:
    @pytest.mark.asyncio
    async def test_positive_headline(self, analyzer):
        score = analyzer.analyze_sentiment("Stocks rally to record highs on strong earnings")
        assert score > 0

    @pytest.mark.asyncio
    async def test_negative_headline(self, analyzer):
        score = analyzer.analyze_sentiment("Market crash wipes out billions amid recession fears")
        assert score < 0

    @pytest.mark.asyncio
    async def test_neutral_headline(self, analyzer):
        score = analyzer.analyze_sentiment("Fed holds interest rates steady at current levels")
        assert -0.5 <= score <= 0.5

    @pytest.mark.asyncio
    async def test_returns_zero_without_initialization(self):
        a = SentimentAnalyzer()
        score = a.analyze_sentiment("This should return 0.0")
        assert score == 0.0
        await a.aclose()

    @pytest.mark.asyncio
    async def test_financial_lexicon_boost(self, analyzer):
        bullish_score = analyzer.analyze_sentiment("bullish")
        bearish_score = analyzer.analyze_sentiment("bearish")
        assert bullish_score > bearish_score


class TestFetchMarketNews:
    @pytest.fixture(autouse=True)
    def set_api_key(self):
        with patch("src.services.sentiment_analyzer.settings") as mock_settings:
            mock_settings.finnhub_api_key = "test-key"
            yield mock_settings

    @pytest.fixture
    async def service(self):
        s = SentimentAnalyzer()
        yield s
        await s.aclose()

    @pytest.mark.asyncio
    async def test_returns_empty_on_api_error(self, service):
        with patch.object(service.client, "get", side_effect=httpx.HTTPError("Network error")):
            result = await service.fetch_market_news()
        assert result == []

    @pytest.mark.asyncio
    async def test_parses_news_correctly(self, service):
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()
        mock_response.json.return_value = [
            {
                "headline": "S&P 500 surges to all-time high",
                "summary": "Markets rally on optimism",
                "source": "Reuters",
                "datetime": 1700000000,
                "url": "https://example.com/article",
            }
        ]

        with patch.object(service.client, "get", new=AsyncMock(return_value=mock_response)):
            result = await service.fetch_market_news()

        assert len(result) == 1
        assert result[0].headline == "S&P 500 surges to all-time high"
        assert result[0].source == "Reuters"
        assert result[0].url == "https://example.com/article"
        assert result[0].published_at.tzinfo == timezone.utc

    @pytest.mark.asyncio
    async def test_limits_to_20_items(self, service):
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()
        mock_response.json.return_value = [
            {
                "headline": f"News {i}",
                "summary": None,
                "source": "Test",
                "datetime": 1700000000,
                "url": None,
            }
            for i in range(30)
        ]

        with patch.object(service.client, "get", new=AsyncMock(return_value=mock_response)):
            result = await service.fetch_market_news()

        assert len(result) == 20

    @pytest.mark.asyncio
    async def test_symbol_uses_company_news_endpoint(self, service):
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()
        mock_response.json.return_value = []

        with patch.object(
            service.client, "get", new=AsyncMock(return_value=mock_response)
        ) as mock_get:
            await service.fetch_market_news(symbol="AAPL")

        call_args = mock_get.call_args
        assert "company-news" in call_args[0][0]
        assert call_args[1]["params"]["symbol"] == "AAPL"

    @pytest.mark.asyncio
    async def test_no_symbol_uses_general_news_endpoint(self, service):
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()
        mock_response.json.return_value = []

        with patch.object(
            service.client, "get", new=AsyncMock(return_value=mock_response)
        ) as mock_get:
            await service.fetch_market_news()

        call_args = mock_get.call_args
        assert "/news" in call_args[0][0]
        assert "company-news" not in call_args[0][0]


class TestGetMarketSentiment:
    @pytest.mark.asyncio
    async def test_returns_neutral_when_no_news(self, analyzer):
        with patch.object(analyzer, "fetch_market_news", new=AsyncMock(return_value=[])):
            result = await analyzer.get_market_sentiment()

        assert result.overall_score == 0.0
        assert result.label == "NEUTRAL"
        assert result.news_count == 0

    @pytest.mark.asyncio
    async def test_positive_label_for_positive_score(self, analyzer):
        from src.services.sentiment_analyzer import NewsItem

        mock_news = [
            NewsItem(
                headline="Stocks rally strongly on bullish earnings",
                summary=None,
                source="Test",
                published_at=datetime.now(timezone.utc),
                url=None,
            )
        ]
        with patch.object(analyzer, "fetch_market_news", new=AsyncMock(return_value=mock_news)):
            result = await analyzer.get_market_sentiment()

        assert result.label in ("POSITIVE", "NEUTRAL")

    @pytest.mark.asyncio
    async def test_negative_label_for_crash_news(self, analyzer):
        from src.services.sentiment_analyzer import NewsItem

        mock_news = [
            NewsItem(
                headline="Market crash bearish recession fears plunge",
                summary=None,
                source="Test",
                published_at=datetime.now(timezone.utc),
                url=None,
            )
        ]
        with patch.object(analyzer, "fetch_market_news", new=AsyncMock(return_value=mock_news)):
            result = await analyzer.get_market_sentiment()

        assert result.label in ("NEGATIVE", "NEUTRAL")
        assert result.news_count == 1

    @pytest.mark.asyncio
    async def test_sentiment_score_is_rounded(self, analyzer):
        from src.services.sentiment_analyzer import NewsItem

        mock_news = [
            NewsItem(
                headline="Moderate market movement",
                summary=None,
                source="Test",
                published_at=datetime.now(timezone.utc),
                url=None,
            )
        ]
        with patch.object(analyzer, "fetch_market_news", new=AsyncMock(return_value=mock_news)):
            result = await analyzer.get_market_sentiment()

        assert result.overall_score == round(result.overall_score, 3)
