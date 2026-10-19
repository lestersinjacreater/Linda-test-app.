"""Mock Safaricom fraud intelligence (demo infrastructure, root CLAUDE.md 5.12).

Run:  uvicorn --factory src.main:create_app --port 8002   (from fraudintel/)
"""
import time
from typing import Callable, Optional

from fastapi import FastAPI

from src.core.config import Settings
from src.features.intel.router import router
from src.features.intel.store import Store


def create_app(settings: Optional[Settings] = None, clock: Callable[[], float] = time.time) -> FastAPI:
    settings = settings or Settings.from_env()
    app = FastAPI(title="Linda mock fraud intelligence")
    store = Store(settings.seed_path, settings.verified_callers_path, settings.inbox_limit, clock)
    store.reset()
    app.state.store = store
    app.include_router(router)

    @app.get("/health")
    def health() -> dict:
        return {"ok": True}

    return app
