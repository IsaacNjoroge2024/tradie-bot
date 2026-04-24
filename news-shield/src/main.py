from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
import logging

from .routers import market_status, events, sentiment
from .services.event_calendar import EventCalendarService
from .services.sentiment_analyzer import SentimentAnalyzer

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting News Shield service...")
    app.state.event_service = EventCalendarService()
    app.state.sentiment_analyzer = SentimentAnalyzer()
    await app.state.sentiment_analyzer.initialize()
    logger.info("News Shield ready")
    yield
    logger.info("Shutting down News Shield...")


app = FastAPI(
    title="Tradie News Shield",
    description="Market condition analysis and news sentiment filtering",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(market_status.router, prefix="/api", tags=["Market Status"])
app.include_router(events.router, prefix="/api", tags=["Economic Events"])
app.include_router(sentiment.router, prefix="/api", tags=["Sentiment"])


@app.get("/health")
async def health():
    return {"status": "healthy", "service": "news-shield"}
