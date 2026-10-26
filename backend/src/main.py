"""The radar service. Run:  uvicorn --factory src.main:create_app --port 8001"""
import asyncio
import contextlib
import time
from typing import Callable, Optional

from fastapi import FastAPI

from src.core.config import Settings
from src.core.db import make_session_factory
from src.features.confirmation.notifier import Notifier, make_telco_notifier
from src.features.confirmation.service import load_verified_senders
from src.features.intel.client import HttpIntelClient, IntelClient
from src.features.intel.sync import sync_intel
from src.features.lookups.router import router as lookups_router
from src.features.private.ratelimit import RateLimiter
from src.features.private.router import router as private_router
from src.features.reports.router import router as reports_router
from src.features.ussd.router import router as ussd_router
from src.features.verified.router import router as verified_router
from src.features.verified.service import load_verified_callers


def create_app(
    settings: Optional[Settings] = None,
    notifier: Optional[Notifier] = None,
    clock: Callable[[], float] = time.time,
    intel_client: Optional[IntelClient] = None,
) -> FastAPI:
    """`notifier`, `clock` and `intel_client` can be swapped in tests (no network, controllable time)."""
    settings = settings or Settings.from_env()
    if intel_client is None and settings.fraudintel_url:
        intel_client = HttpIntelClient(settings.fraudintel_url)

    @contextlib.asynccontextmanager
    async def lifespan(app: FastAPI):
        """Pull fraud intelligence every few seconds while the server runs (only if it is configured)."""
        task = asyncio.create_task(_intel_loop(app)) if app.state.intel_client is not None else None
        yield
        if task:
            task.cancel()

    app = FastAPI(title="Linda gateway", lifespan=lifespan)
    app.state.settings = settings
    app.state.sessions = make_session_factory(settings.database_url)
    app.state.verified = load_verified_senders(settings)
    app.state.verified_callers_version, app.state.verified_callers = load_verified_callers(settings.verified_callers_path)
    app.state.notifier = notifier or make_telco_notifier(settings)
    app.state.clock = clock
    app.state.intel_client = intel_client
    app.state.lookup_limiter = RateLimiter(settings.lookup_rate_per_hour, clock)
    app.state.intel_cursor = None  # "as_of" of the last fraud-intel sync
    app.state.intel_last = {"enabled": intel_client is not None}
    app.include_router(reports_router)
    app.include_router(lookups_router)
    app.include_router(verified_router)
    app.include_router(private_router)
    app.include_router(ussd_router)

    @app.get("/health")
    def health() -> dict:
        return {"ok": True, "intel": app.state.intel_last}

    return app


async def _intel_loop(app: FastAPI) -> None:
    while True:
        app.state.intel_last = await asyncio.to_thread(sync_intel, app)
        await asyncio.sleep(app.state.settings.intel_sync_s)
