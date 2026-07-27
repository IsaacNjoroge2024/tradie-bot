import pytest

# Skip the entire module if the ml extras (torch/transformers) are not installed.
# Both are guarded separately: transformers can be installed with a non-torch
# backend (TensorFlow/JAX), so a bare "transformers" check alone wouldn't skip
# cleanly in that case — finbert_analyzer.py imports torch directly.
pytest.importorskip(
    "transformers", reason="transformers/torch not installed; run: pip install -e '.[ml,dev]'"
)
pytest.importorskip(
    "torch", reason="transformers/torch not installed; run: pip install -e '.[ml,dev]'"
)

from src.sentiment.finbert_analyzer import FinBERTAnalyzer


@pytest.fixture(scope="module")
def analyzer():
    return FinBERTAnalyzer(device="cpu")


class TestFinBERTAnalyzer:

    def test_positive_sentiment(self, analyzer):
        result = analyzer.analyze("Company reports record earnings, stock surges 20%")
        assert result.label == "positive"
        assert result.compound > 0.3

    def test_negative_sentiment(self, analyzer):
        result = analyzer.analyze("Company files for bankruptcy amid mounting debt")
        assert result.label == "negative"
        assert result.compound < -0.3

    def test_neutral_sentiment(self, analyzer):
        # A range check rather than an exact label: near the classification
        # boundary, a probabilistic model's top label can be sensitive to
        # minor wording/version differences even when the score itself is
        # consistently close to zero (i.e. genuinely neutral-ish).
        result = analyzer.analyze("Company announces quarterly earnings report next week")
        assert abs(result.compound) < 0.3

    def test_batch_processing(self, analyzer):
        texts = [
            "Stock hits all-time high",
            "Company faces lawsuit",
            "Board meeting scheduled",
        ]
        results = analyzer.analyze_batch(texts)

        assert len(results) == 3
        assert results[0].label == "positive"
        assert results[1].label == "negative"

    def test_batch_matches_single_text_analysis(self, analyzer):
        text = "Company reports record earnings, stock surges 20%"
        single = analyzer.analyze(text)
        batch = analyzer.analyze_batch([text])[0]

        assert single.label == batch.label
        assert single.compound == pytest.approx(batch.compound)

    def test_confidence_is_highest_class_probability(self, analyzer):
        result = analyzer.analyze("Company reports record earnings, stock surges 20%")
        scores = [result.positive_score, result.negative_score, result.neutral_score]
        assert result.confidence == max(scores)
