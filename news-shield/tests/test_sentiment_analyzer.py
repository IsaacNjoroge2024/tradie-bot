import httpx
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from src.services.sentiment_analyzer import SentimentAnalyzer


@pytest.fixture
async def analyzer():
    # Force VADER-only initialization for these pre-existing tests, regardless
    # of whether the optional `ml` extras (torch/transformers) happen to be
    # installed locally — keeps this fixture fast and deterministic, and these
    # tests are specifically about VADER behavior. FinBERT-primary behavior is
    # covered separately in TestFinBERTHybridBehavior below.
    with patch("src.services.sentiment_analyzer.settings") as mock_settings:
        mock_settings.sentiment_primary_analyzer = "vader"
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


class TestFinBERTLoading:
    """These exercise the real _load_finbert() path. In CI (no `ml` extras
    installed) this naturally validates graceful fallback via a real
    ModuleNotFoundError — not a mocked one. If `ml` extras ARE installed
    locally, this genuinely loads FinBERT (slower, but still correct)."""

    @pytest.mark.asyncio
    async def test_load_finbert_never_raises(self):
        a = SentimentAnalyzer()
        a._load_finbert()
        assert a.finbert_model is None or hasattr(a.finbert_model, "analyze")
        await a.aclose()

    @pytest.mark.asyncio
    async def test_initialize_skips_finbert_when_vader_selected(self):
        with patch("src.services.sentiment_analyzer.settings") as mock_settings:
            mock_settings.sentiment_primary_analyzer = "vader"
            a = SentimentAnalyzer()
            await a.initialize()
        assert a.finbert_model is None
        await a.aclose()


class TestFinBERTHybridBehavior:
    """Tests the FinBERT-primary/VADER-fallback logic using a mocked FinBERT
    model, so these run correctly without the heavy `ml` extras installed."""

    @pytest.fixture
    async def analyzer_with_mock_finbert(self):
        with patch("src.services.sentiment_analyzer.settings") as mock_settings:
            mock_settings.sentiment_primary_analyzer = "vader"
            mock_settings.finbert_batch_size = 16
            a = SentimentAnalyzer()
            await a.initialize()
        a.finbert_model = MagicMock()
        yield a
        await a.aclose()

    @staticmethod
    def _finbert_result(text: str, label: str, compound: float, confidence: float = 0.9):
        return MagicMock(
            text=text,
            label=label,
            compound=compound,
            confidence=confidence,
            positive_score=max(compound, 0.0),
            negative_score=max(-compound, 0.0),
            neutral_score=1.0 - abs(compound),
        )

    @pytest.mark.asyncio
    async def test_analyze_text_prefers_finbert(self, analyzer_with_mock_finbert):
        a = analyzer_with_mock_finbert
        a.finbert_model.analyze.return_value = self._finbert_result("t", "positive", 0.8)

        result = a.analyze_text("Company reports record earnings")

        assert result.engine == "finbert"
        assert result.label == "POSITIVE"
        assert result.compound == 0.8
        a.finbert_model.analyze.assert_called_once()

    @pytest.mark.asyncio
    async def test_analyze_text_falls_back_to_vader_on_finbert_error(
        self, analyzer_with_mock_finbert
    ):
        a = analyzer_with_mock_finbert
        a.finbert_model.analyze.side_effect = RuntimeError("CUDA out of memory")

        result = a.analyze_text("Stocks rally to record highs on strong earnings")

        assert result.engine == "vader"
        assert result.compound > 0

    @pytest.mark.asyncio
    async def test_analyze_sentiment_uses_finbert_compound(self, analyzer_with_mock_finbert):
        a = analyzer_with_mock_finbert
        a.finbert_model.analyze.return_value = self._finbert_result("t", "negative", -0.6)

        assert a.analyze_sentiment("Company files for bankruptcy") == -0.6

    @pytest.mark.asyncio
    async def test_analyze_texts_batch_prefers_finbert(self, analyzer_with_mock_finbert):
        a = analyzer_with_mock_finbert
        a.finbert_model.analyze_batch.return_value = [
            self._finbert_result("Stock hits all-time high", "positive", 0.7),
            self._finbert_result("Company faces lawsuit", "negative", -0.6),
        ]

        results = a.analyze_texts_batch(["Stock hits all-time high", "Company faces lawsuit"])

        assert len(results) == 2
        assert results[0].label == "POSITIVE"
        assert results[1].label == "NEGATIVE"
        assert all(r.engine == "finbert" for r in results)

    @pytest.mark.asyncio
    async def test_analyze_texts_batch_falls_back_to_vader_on_finbert_error(
        self, analyzer_with_mock_finbert
    ):
        a = analyzer_with_mock_finbert
        a.finbert_model.analyze_batch.side_effect = RuntimeError("model error")

        results = a.analyze_texts_batch(["Markets rally on positive jobs report"])

        assert len(results) == 1
        assert results[0].engine == "vader"

    @pytest.mark.asyncio
    async def test_analyze_texts_batch_empty_input(self, analyzer_with_mock_finbert):
        assert analyzer_with_mock_finbert.analyze_texts_batch([]) == []

    @pytest.mark.asyncio
    async def test_aggregate_sentiment_empty_input(self, analyzer_with_mock_finbert):
        summary = analyzer_with_mock_finbert.aggregate_sentiment([])
        assert summary.overall_sentiment == "NEUTRAL"
        assert summary.headline_count == 0

    @pytest.mark.asyncio
    async def test_aggregate_sentiment_computes_percentages(self, analyzer_with_mock_finbert):
        a = analyzer_with_mock_finbert
        a.finbert_model.analyze_batch.return_value = [
            self._finbert_result("Markets rally on positive jobs report", "positive", 0.6),
            self._finbert_result("Tech stocks lead gains", "positive", 0.5),
            self._finbert_result("Fed signals rate cuts ahead", "neutral", 0.05),
            self._finbert_result("Investors optimistic about Q4", "positive", 0.7),
        ]

        summary = a.aggregate_sentiment(
            [
                "Markets rally on positive jobs report",
                "Tech stocks lead gains",
                "Fed signals rate cuts ahead",
                "Investors optimistic about Q4",
            ]
        )

        assert summary.overall_sentiment == "POSITIVE"
        assert summary.headline_count == 4
        assert summary.positive_pct == 0.75
        assert summary.compound_score > 0.2


class TestFetchMarketNews:
    @pytest.fixture(autouse=True)
    def set_api_key(self):
        with patch("src.services.sentiment_analyzer.settings") as mock_settings:
            mock_settings.finnhub_api_key = "test-key"
            mock_settings.alpha_vantage_api_key = ""  # disable fallback by default
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

    @pytest.mark.asyncio
    async def test_falls_back_to_alpha_vantage_when_finnhub_empty(self, service, set_api_key):
        set_api_key.alpha_vantage_api_key = "av-key"

        finnhub_response = MagicMock()
        finnhub_response.raise_for_status = MagicMock()
        finnhub_response.json.return_value = []

        av_response = MagicMock()
        av_response.raise_for_status = MagicMock()
        av_response.json.return_value = {
            "feed": [
                {
                    "title": "Markets surge on Fed pivot hopes",
                    "summary": "Investors cheer dovish signals",
                    "source": "Bloomberg",
                    "time_published": "20240115T120000",
                    "url": "https://example.com/av-article",
                }
            ]
        }

        responses = [finnhub_response, av_response]
        with patch.object(service.client, "get", new=AsyncMock(side_effect=responses)):
            result = await service.fetch_market_news()

        assert len(result) == 1
        assert result[0].headline == "Markets surge on Fed pivot hopes"
        assert result[0].source == "Bloomberg"

    @pytest.mark.asyncio
    async def test_no_fallback_when_alpha_vantage_key_missing(self, service):
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()
        mock_response.json.return_value = []

        with patch.object(
            service.client, "get", new=AsyncMock(return_value=mock_response)
        ) as mock_get:
            result = await service.fetch_market_news()

        assert result == []
        assert mock_get.call_count == 1  # only Finnhub, no Alpha Vantage attempt

    @pytest.mark.asyncio
    async def test_alpha_vantage_daily_limit_returns_empty(self, service, set_api_key):
        set_api_key.alpha_vantage_api_key = "av-key"

        finnhub_response = MagicMock()
        finnhub_response.raise_for_status = MagicMock()
        finnhub_response.json.return_value = []

        av_response = MagicMock()
        av_response.raise_for_status = MagicMock()
        av_response.json.return_value = {
            "Information": "Thank you for using Alpha Vantage! Our standard API rate limit..."
        }

        with patch.object(
            service.client, "get", new=AsyncMock(side_effect=[finnhub_response, av_response])
        ) as mock_get:
            result = await service.fetch_market_news()

        assert result == []
        assert mock_get.call_count == 2

    @pytest.mark.asyncio
    async def test_alpha_vantage_per_minute_limit_returns_empty(self, service, set_api_key):
        set_api_key.alpha_vantage_api_key = "av-key"

        finnhub_response = MagicMock()
        finnhub_response.raise_for_status = MagicMock()
        finnhub_response.json.return_value = []

        av_response = MagicMock()
        av_response.raise_for_status = MagicMock()
        av_response.json.return_value = {
            "Note": "Thank you for using Alpha Vantage! Our standard API call frequency is 5 calls per minute..."
        }

        with patch.object(
            service.client, "get", new=AsyncMock(side_effect=[finnhub_response, av_response])
        ) as mock_get:
            result = await service.fetch_market_news()

        assert result == []
        assert mock_get.call_count == 2


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
