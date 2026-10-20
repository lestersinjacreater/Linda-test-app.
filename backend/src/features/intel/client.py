"""Talks to the fraud team's systems (contract 5.12): `fraudintel/` in the demo, Safaricom's in production.

The gateway only ever uses this small interface, so tests swap in a fake with no network.
"""
import logging
from typing import Optional, Protocol

import httpx

log = logging.getLogger("gateway.intel")


class IntelClient(Protocol):
    def numbers(self, since: Optional[str]) -> dict: ...
    def verified(self) -> dict: ...
    def campaigns(self) -> dict: ...
    def forward(self, item: dict) -> None: ...


class HttpIntelClient:
    def __init__(self, base_url: str, timeout: float = 5.0):
        self.base_url, self.timeout = base_url, timeout

    def _get(self, path: str, params: Optional[dict] = None) -> dict:
        r = httpx.get(f"{self.base_url}{path}", params=params, timeout=self.timeout)
        r.raise_for_status()
        return r.json()

    def numbers(self, since: Optional[str]) -> dict:
        return self._get("/v1/intel/numbers", {"since": since} if since else None)

    def verified(self) -> dict:
        return self._get("/v1/intel/verified")

    def campaigns(self) -> dict:
        return self._get("/v1/intel/campaigns")

    def forward(self, item: dict) -> None:
        """Best effort: a failure is logged, never shown to the phone."""
        try:
            httpx.post(f"{self.base_url}/v1/intel/reports", json=item, timeout=self.timeout).raise_for_status()
        except Exception as exc:
            log.warning("could not forward %s for %s: %s", item.get("kind"), item.get("sender"), exc)
