from datetime import datetime
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from src.services.sentiment_analyzer import SentimentAnalyzer, SentimentResult


@pytest.fixture
async def analyzer():
    a = SentimentAnalyzer()
    await a.initialize()
    return a


class TestSentimentAnalyzerInit:
    @pytest.mark.asyncio
    async def test_initialize_loads_vader(self):
        a = SentimentAnalyzer()
        assert a.vader is None
        await a.initialize()
        assert a.vader is not None

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

    @pytest.mark.asyncio
    async def test_financial_lexicon_boost(self, analyzer):
        bullish_score = analyzer.analyze_sentiment("bullish")
        bearish_score = analyzer.analyze_sentiment("bearish")
        assert bullish_score > bearish_score


class TestFetchMarketNews:
    @pytest.fixture
    def service(self):
        return SentimentAnalyzer()

    @pytest.mark.asyncio
    async def test_returns_empty_on_api_error(self, service):
        with patch.object(service.client, "get", side_effect=Exception("Network error")):
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

    @pytest.mark.asyncio
    async def test_limits_to_20_items(self, service):
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()
        mock_response.json.return_value = [
            {"headline": f"News {i}", "summary": None, "source": "Test", "datetime": 0, "url": None}
            for i in range(30)
        ]

        with patch.object(service.client, "get", new=AsyncMock(return_value=mock_response)):
            result = await service.fetch_market_news()

        assert len(result) == 20


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
                published_at=datetime.now(),
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
                published_at=datetime.now(),
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
                published_at=datetime.now(),
                url=None,
            )
        ]
        with patch.object(analyzer, "fetch_market_news", new=AsyncMock(return_value=mock_news)):
            result = await analyzer.get_market_sentiment()

        # Score should be rounded to 3 decimal places
        assert result.overall_score == round(result.overall_score, 3)
