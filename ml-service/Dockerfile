FROM python:3.11-slim AS builder

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Copy source before install so setuptools can discover the src package
COPY pyproject.toml .
COPY src/ ./src/
COPY config/ ./config/
RUN pip install --no-cache-dir --prefix=/install .

FROM python:3.11-slim

WORKDIR /app

# gcc is only needed to build wheels — copy the installed packages/console
# scripts and app source, not the compiler toolchain, into the runtime image.
COPY --from=builder /install /usr/local
COPY --from=builder /app/src ./src
COPY --from=builder /app/config ./config

# Run as non-root for security
RUN useradd --create-home --uid 1001 appuser \
    && chown -R appuser:appuser /app /home/appuser
USER appuser

CMD ["uvicorn", "src.main:app", "--host", "0.0.0.0", "--port", "8003"]
