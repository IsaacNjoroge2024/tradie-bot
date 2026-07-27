FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Copy source before install so setuptools can discover the src package
COPY pyproject.toml .
COPY src/ ./src/
COPY config/ ./config/
RUN pip install --no-cache-dir .

# Run as non-root for security
RUN useradd --create-home --uid 1001 appuser \
    && chown -R appuser:appuser /app /home/appuser
USER appuser

CMD ["uvicorn", "src.main:app", "--host", "0.0.0.0", "--port", "8003"]
