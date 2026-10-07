"""The radar service. Run:  uvicorn --factory src.main:create_app --port 8001"""
import time
from typing import Callable, Optional

from fastapi import FastAPI

from src.core.config import Settings
from src.core.db import make_session_factory
from src.features.confirmation.notifier import Notifier, make_telco_notifier
from src.features.confirmation.service import load_verified_senders
from src.features.lookups.router import router as lookups_router
from src.features.reports.router import router as reports_router


def create_app(
    settings: Optional[Settings] = None,
    notifier: Optional[Notifier] = None,
    clock: Callable[[], float] = time.time,
) -> FastAPI:
    """`notifier` and `clock` can be swapped in tests (no network, controllable time)."""
    settings = settings or Settings.from_env()
    app = FastAPI(title="Linda radar")
    app.state.settings = settings
    app.state.sessions = make_session_factory(settings.database_url)
    app.state.verified = load_verified_senders(settings)
    app.state.notifier = notifier or make_telco_notifier(settings)
    app.state.clock = clock
    app.include_router(reports_router)
    app.include_router(lookups_router)

    @app.get("/health")
    def health() -> dict:
        return {"ok": True}

    return app

