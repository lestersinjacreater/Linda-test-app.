"""GET /v1/stats (contract 5.14): real counts for the dashboard. Read-only, no full numbers, no message text."""
from fastapi import APIRouter, Request

from src.features.stats.service import stats

router = APIRouter()


@router.get("/v1/stats")
def get_stats(request: Request) -> dict:
    return stats(request.app)
