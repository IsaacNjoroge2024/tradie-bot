FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Pin NLTK data to a shared path accessible by any user at runtime
ENV NLTK_DATA=/usr/local/share/nltk_data

# Copy source before install so setuptools can discover the src package
COPY pyproject.toml .
COPY src/ ./src/
RUN pip install --no-cache-dir .

RUN python -c "import nltk; nltk.download('vader_lexicon', download_dir='/usr/local/share/nltk_data', quiet=True)"

# Run as non-root for security
RUN useradd --create-home --uid 1001 appuser \
    && chown -R appuser:appuser /app /home/appuser /usr/local/share/nltk_data
USER appuser

CMD ["uvicorn", "src.main:app", "--host", "0.0.0.0", "--port", "8001"]
