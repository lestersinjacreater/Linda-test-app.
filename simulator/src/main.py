"""The mock telco. Run:  uvicorn --factory src.main:create_app --port 8000

DEMO INFRASTRUCTURE, not part of the product. It stands in for Safaricom's network and talks to
the radar only through the contracts in the root CLAUDE.md (5.1, 5.4, 5.5).
"""
from typing import Optional

from fastapi import FastAPI

from src.core.config import Settings
from src.core.events import EventBus
from src.features.engine.engine import Engine
from src.features.engine.radar_client import HttpRadarClient, RadarClient
from src.features.network.router import router as network_router
from src.features.scenarios.router import router as scenarios_router


def create_app(settings: Optional[Settings] = None, radar: Optional[RadarClient] = None) -> FastAPI:
    settings = settings or Settings.from_env()
    app = FastAPI(title="Linda mock telco")
    app.state.settings = settings
    app.state.bus = EventBus()
    app.state.engine = Engine(settings, radar or HttpRadarClient(settings.radar_url), app.state.bus)
    app.include_router(network_router)
    app.include_router(scenarios_router)

    @app.get("/health")
    def health() -> dict:
        return {"ok": True, "model_version": app.state.engine.scorer.version}

    return app
