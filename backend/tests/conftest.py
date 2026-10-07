from dataclasses import replace

import pytest
from fastapi.testclient import TestClient

from src.core.config import Settings
from src.core.db import Device
from src.main import create_app


class FakeClock:
    def __init__(self, start: float = 1_800_000_000.0):
        self.now = start

    def __call__(self) -> float:
        return self.now

    def advance(self, seconds: float) -> None:
        self.now += seconds


class Radar:
    """A test radar with no network, controllable time and a record of who the telco was told about."""

    def __init__(self, **overrides):
        self.settings = replace(Settings(database_url="sqlite:///:memory:"), **overrides)
        self.clock = FakeClock()
        self.notified: list[str] = []
        self.app = create_app(self.settings, notifier=self.notified.append, clock=self.clock)
        self.client = TestClient(self.app)

    def report(self, sender="254700000050", device="a" * 16, confidence=0.9, category="fake_mpesa",
               fingerprint="426183190df2b092", token=None, **extra):
        body = {
            "sender": sender, "category": category, "confidence": confidence, "fingerprint": fingerprint,
            "model_version": "test-1", "device_id": device, "sent_at": "2026-10-20T14:02:11Z", **extra,
        }
        if token:
            body["integrity_token"] = token
        return self.client.post("/v1/reports", json=body)

    def set_trust(self, device_id: str, trust: float, token: bool = True):
        """Make a device established, as if it had reported confirmed scams before."""
        with self.app.state.sessions() as s:
            d = s.get(Device, device_id)
            d.trust, d.has_integrity_token = trust, token
            s.commit()

    def risk(self, msisdn):
        return self.client.get(f"/v1/numbers/{msisdn}/risk").json()


def device(n: int) -> str:
    return f"{n:016x}"


@pytest.fixture
def radar():
    return Radar()
