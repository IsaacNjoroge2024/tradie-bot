from fastapi import FastAPI

app = FastAPI(
    title="News Shield",
    description="Market condition analysis service for Tradie Bot",
    version="1.0.0",
)


@app.get("/health")
async def health():
    return {"status": "ok", "service": "news-shield"}
