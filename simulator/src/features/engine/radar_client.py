"""The simulator's only link to the radar: POST /v1/reports (contract 5.1)."""
from typing import Any, Optional, Protocol

import httpx


class RadarClient(Protocol):
    async def report(self, payload: dict[str, Any]) -> Optional[dict[str, Any]]:
        """Return the radar's JSON answer, or None when the radar could not be reached or refused."""


class HttpRadarClient:
    def __init__(self, base_url: str):
        self._http = httpx.AsyncClient(base_url=base_url, timeout=5.0)

    async def report(self, payload: dict[str, Any]) -> Optional[dict[str, Any]]:
        try:
            r = await self._http.post("/v1/reports", json=payload)
            r.raise_for_status()
            return r.json()
        except Exception:
            return None

    async def close(self) -> None:
        await self._http.aclose()
