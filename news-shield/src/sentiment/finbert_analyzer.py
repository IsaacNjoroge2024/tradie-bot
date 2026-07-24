"""FinBERT-based sentiment analysis for financial text (Ticket 23).

Requires the optional `ml` extras (torch, transformers) — see pyproject.toml.
This module is only imported lazily, inside SentimentAnalyzer._load_finbert(),
so the base service still runs (VADER-only) without these heavy deps installed.
"""

import logging
from dataclasses import dataclass
from typing import ClassVar, List, Optional

import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

logger = logging.getLogger(__name__)


@dataclass
class FinBERTResult:
    """Result from FinBERT sentiment analysis of a single text."""

    text: str
    label: str  # positive, negative, neutral
    positive_score: float
    negative_score: float
    neutral_score: float
    compound: float  # positive_score - negative_score, -1 to 1 (VADER-like)
    confidence: float  # highest of the three class probabilities


class FinBERTAnalyzer:
    """
    Finance-specific BERT model for sentiment analysis.

    Uses ProsusAI/finbert, pre-trained on financial news and reports —
    significantly more accurate than VADER for financial text.
    """

    MODEL_NAME = "ProsusAI/finbert"
    LABELS: ClassVar[List[str]] = ["positive", "negative", "neutral"]  # FinBERT's output order

    def __init__(self, device: Optional[str] = None, max_length: int = 512):
        """
        Args:
            device: 'cuda', 'cpu', or None (auto-detect)
            max_length: Max token length per text before truncation
        """
        self.device = device or ("cuda" if torch.cuda.is_available() else "cpu")
        self.max_length = max_length
        logger.info(f"Loading FinBERT model on {self.device}...")

        self.tokenizer = AutoTokenizer.from_pretrained(self.MODEL_NAME)
        self.model = AutoModelForSequenceClassification.from_pretrained(self.MODEL_NAME)
        self.model.to(self.device)
        self.model.eval()

        logger.info("FinBERT model loaded successfully")

    def analyze(self, text: str) -> FinBERTResult:
        """Analyze sentiment of a single text."""
        return self.analyze_batch([text])[0]

    def analyze_batch(self, texts: List[str], batch_size: int = 16) -> List[FinBERTResult]:
        """
        Analyze sentiment of multiple texts efficiently, in chunks of batch_size.

        Args:
            texts: List of news headlines/articles
            batch_size: Number of texts tokenized/inferred per forward pass

        Returns:
            List of FinBERTResult, one per input text, in the same order
        """
        results: List[FinBERTResult] = []

        for i in range(0, len(texts), batch_size):
            chunk = texts[i : i + batch_size]

            inputs = self.tokenizer(
                chunk,
                return_tensors="pt",
                truncation=True,
                max_length=self.max_length,
                padding=True,
            )
            inputs = {k: v.to(self.device) for k, v in inputs.items()}

            with torch.no_grad():
                outputs = self.model(**inputs)
                probabilities = torch.softmax(outputs.logits, dim=1)

            for j, text in enumerate(chunk):
                probs = probabilities[j]
                positive_score = float(probs[0])
                negative_score = float(probs[1])
                neutral_score = float(probs[2])

                scores = {
                    "positive": positive_score,
                    "negative": negative_score,
                    "neutral": neutral_score,
                }
                label = max(scores, key=scores.get)

                results.append(
                    FinBERTResult(
                        text=text,
                        label=label,
                        positive_score=positive_score,
                        negative_score=negative_score,
                        neutral_score=neutral_score,
                        compound=positive_score - negative_score,
                        confidence=scores[label],
                    )
                )

        return results
