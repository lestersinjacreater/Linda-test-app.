"""The gateway learning from the fraud team (root CLAUDE.md 5.12), with a fake fraud service (no network)."""
from dataclasses import replace

from fastapi.testclient import TestClient

from src.core.config import Settings
from src.features.intel.sync import sync_intel
from src.main import create_app
from tests.conftest import FakeClock

FP = "426183190df2b092"  # a campaign fingerprint the fake fraud team knows


class FakeIntel:
    def __init__(self, numbers=None, up=True):
        self.numbers_list = numbers if numbers is not None else [
            {"msisdn": "254700000777", "status": "blocked", "category": "fake_mpesa", "reports": 41, "campaign_id": "c1"},
            {"msisdn": "254700000802", "status": "campaign", "category": "prize", "reports": 9, "campaign_id": "c2"},
            {"msisdn": "254700000803", "status": "reported", "category": "pin_request", "reports": 2, "campaign_id": None},
            {"msisdn": "254722000000", "status": "blocked", "category": "other", "reports": 3, "campaign_id": None},
        ]
        self.up = up
        self.forwarded: list[dict] = []
        self.since_seen: list = []

    def _check(self):
        if not self.up:
            raise ConnectionError("fraud service down")

    def numbers(self, since):
        self._check()
        self.since_seen.append(since)
        return {"as_of": "2027-01-15T08:00:00Z", "numbers": self.numbers_list}

    def verified(self):
        self._check()
        return {"numbers": [{"msisdn": "254711000999", "name_en": "Demo Bank", "name_sw": "Benki ya Majaribio", "kind": "bank"}]}

    def campaigns(self):
        self._check()
        return {"campaigns": [{"id": "c1", "category": "fake_mpesa", "fingerprints": [FP]}]}

    def forward(self, item):
        self.forwarded.append(item)


class Gateway:
    def __init__(self, intel=None):
        self.intel = intel if intel is not None else FakeIntel()
        self.notified: list[str] = []
        self.app = create_app(replace(Settings(database_url="sqlite:///:memory:")),
                              notifier=self.notified.append, clock=FakeClock(), intel_client=self.intel)
        self.client = TestClient(self.app)

    def sync(self):
        return sync_intel(self.app)

    def risk(self, msisdn):
        return self.client.get(f"/v1/numbers/{msisdn}/risk").json()

    def blocklist(self):
        return {a["msisdn"] for a in self.client.get("/v1/blocklist").json()["added"]}

    def report(self, sender, device, fingerprint="0000000000000000"):
        return self.client.post("/v1/reports", json={
            "sender": sender, "category": "fake_mpesa", "confidence": 0.9, "fingerprint": fingerprint,
            "model_version": "test-1", "device_id": device, "sent_at": "2026-10-20T14:02:11Z"})


def test_blocked_and_campaign_numbers_are_confirmed_and_reach_phones():
    g = Gateway()
    summary = g.sync()
    assert summary["ok"] and set(summary["newly_confirmed"]) == {"254700000777", "254700000802"}
    assert g.risk("254700000777")["status"] == "confirmed" and g.risk("254700000777")["category"] == "fake_mpesa"
    assert {"254700000777", "254700000802"} <= g.blocklist()
    assert set(g.notified) == {"254700000777", "254700000802"}  # the network is told, so it can warn everyone


def test_a_number_only_reported_to_333_is_suspected_but_never_on_the_phone_blocklist():
    g = Gateway()
    g.sync()
    r = g.risk("254700000803")
    assert r["status"] == "suspected" and r["reports"] == 2 and r["category"] == "pin_request"
    assert "254700000803" not in g.blocklist()


def test_verified_numbers_win_over_anything_the_fraud_feed_says():
    g = Gateway()
    g.sync()
    assert g.risk("254722000000")["status"] == "verified"
    assert "254722000000" not in g.blocklist() and "254722000000" not in g.notified


def test_the_fraud_teams_verified_list_is_merged():
    g = Gateway()
    g.sync()
    assert g.risk("254711000999")["status"] == "verified"
    callers = {c["msisdn"] for c in g.client.get("/v1/verified-callers").json()["callers"]}
    assert {"254722000000", "254711000999"} <= callers


def test_a_new_number_sending_a_known_campaign_is_suspected_at_once():
    g = Gateway()
    g.sync()
    r = g.report("254700000950", "a" * 16, fingerprint=FP)
    assert r.json()["status"] == "suspected"  # one report, but it matches the fraud team's campaign


def test_reports_and_confirmations_flow_back_without_message_text():
    g = Gateway()
    for d in ("a", "b", "c"):
        g.report("254700000960", d * 16)
    kinds = [f["kind"] for f in g.intel.forwarded]
    assert kinds.count("report") == 3
    assert all("text" not in f and f["sender"] == "254700000960" for f in g.intel.forwarded)


def test_the_cursor_moves_so_only_changes_are_pulled():
    g = Gateway()
    g.sync()
    g.sync()
    assert g.intel.since_seen == [None, "2027-01-15T08:00:00Z"]


def test_confirming_twice_notifies_once():
    g = Gateway()
    g.sync()
    g.sync()
    assert g.notified.count("254700000777") == 1


def test_fraud_service_down_changes_nothing():
    g = Gateway(FakeIntel(up=False))
    assert g.sync() == {"enabled": True, "ok": False}
    assert g.risk("254700000777")["status"] == "unknown"
    assert g.report("254700000970", "a" * 16).status_code == 200


def test_without_a_fraud_service_the_gateway_works_as_before():
    app = create_app(Settings(database_url="sqlite:///:memory:"), notifier=lambda s: None, clock=FakeClock())
    assert sync_intel(app) == {"enabled": False}
    assert TestClient(app).get("/health").json()["intel"] == {"enabled": False}
