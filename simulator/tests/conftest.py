import asyncio
from dataclasses import replace

import httpx
import pytest

from src.core.config import Settings
from src.main import create_app


class FakeRadar:
    """Stands in for the real radar: confirms a sender once 3 devices with the trusted demo prefix have reported it.

    It records every payload so tests can check what the simulator sends.
    """

    def __init__(self, prefix: str = "5111", online: bool = True, known: dict | None = None):
        self.prefix, self.online = prefix, online
        self.known = known or {}  # msisdn -> status the gateway already has (e.g. from the fraud team)
        self.risk_lookups: list[str] = []
        self.payloads: list[dict] = []
        self.devices: dict[str, set] = {}
        self.confirmed: set[str] = set()
        self.engine = None

    async def report(self, payload):
        if not self.online:
            return None
        self.payloads.append(payload)
        s = payload["sender"]
        self.devices.setdefault(s, set()).add(payload["device_id"])
        trusted = {d for d in self.devices[s] if d.startswith(self.prefix)}
        if len(trusted) >= 3 and s not in self.confirmed:
            self.confirmed.add(s)
            asyncio.create_task(self.engine.handle_confirm(s))  # the radar's callback to the telco
        status = "confirmed" if s in self.confirmed else ("suspected" if len(self.devices[s]) >= 2 else "unknown")
        return {"status": status, "devices": len(self.devices[s])}

    async def risk(self, msisdn):
        if not self.online:
            return None
        self.risk_lookups.append(msisdn)
        return {"msisdn": msisdn, "status": self.known.get(msisdn, "unknown"), "category": "fake_mpesa",
                "reports": 0, "confirmed_at": None}


class FakeSms:
    """Stands in for Africa's Talking: records what would have been sent."""

    def __init__(self, status: str = "sent"):
        self.status = status
        self.sent: list[tuple[str, str]] = []

    async def send(self, msisdn, message):
        self.sent.append((msisdn, message))
        return self.status


@pytest.fixture
def make_app():
    def build(radar=None, sms=None, **overrides):
        settings = replace(Settings(sim_speed=0, confirm_wait_s=2.0), **overrides)
        radar = radar or FakeRadar()
        app = create_app(settings, radar, sms)
        radar.engine = app.state.engine
        return app, radar
    return build


@pytest.fixture
def client_for():
    def make(app):
        return httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://sim")
    return make
