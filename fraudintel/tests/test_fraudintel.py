"""The mock fraud team: seeded knowledge, the 5.12 contract, and the presenter controls."""
from fastapi.testclient import TestClient

from src.main import create_app


class Clock:
    def __init__(self):
        self.now = 1_800_000_000.0

    def __call__(self):
        return self.now


def make():
    clock = Clock()
    return TestClient(create_app(clock=clock)), clock


def test_seed_has_blocked_reported_and_campaign_numbers_on_placeholder_numbers():
    client, _ = make()
    numbers = client.get("/v1/intel/numbers").json()["numbers"]
    assert {n["status"] for n in numbers} == {"reported", "blocked", "campaign"}
    assert all(n["msisdn"].startswith("254700000") for n in numbers)
    assert {"msisdn": "254700000777", "status": "blocked"}.items() <= {
        k: v for n in numbers if n["msisdn"] == "254700000777" for k, v in n.items()}.items()


def test_since_returns_only_changes():
    client, clock = make()
    as_of = client.get("/v1/intel/numbers").json()["as_of"]
    clock.now += 10
    assert client.get("/v1/intel/numbers", params={"since": as_of}).json()["numbers"] == []
    client.post("/admin/numbers", json={"msisdn": "254700000805", "status": "blocked", "category": "prize"})
    changed = client.get("/v1/intel/numbers", params={"since": as_of}).json()["numbers"]
    assert [n["msisdn"] for n in changed] == ["254700000805"]


def test_verified_list_comes_from_the_shared_file_and_cannot_be_flagged():
    client, _ = make()
    verified = client.get("/v1/intel/verified").json()["numbers"]
    assert any(v["msisdn"] == "254722000000" and v["name_en"] == "Safaricom Customer Care" for v in verified)
    r = client.post("/admin/numbers", json={"msisdn": "254722000000", "status": "blocked"})
    assert r.status_code == 409


def test_campaigns_have_fingerprints():
    campaigns = make()[0].get("/v1/intel/campaigns").json()["campaigns"]
    assert {c["category"] for c in campaigns} >= {"fake_mpesa", "sent_by_mistake", "prize"}
    assert all(len(fp) == 16 for c in campaigns for fp in c["fingerprints"])


def test_forwarded_reports_land_in_the_inbox_and_count():
    client, _ = make()
    before = {n["msisdn"]: n for n in client.get("/v1/intel/numbers").json()["numbers"]}["254700000803"]["reports"]
    r = client.post("/v1/intel/reports", json={"kind": "report", "sender": "254700000803", "category": "pin_request",
                                                "confidence": 0.9, "at": "2026-10-20T14:02:11Z"})
    assert r.status_code == 200
    inbox = client.get("/v1/intel/reports").json()["reports"]
    assert inbox[0]["sender"] == "254700000803" and inbox[0]["kind"] == "report"
    after = {n["msisdn"]: n for n in client.get("/v1/intel/numbers").json()["numbers"]}["254700000803"]["reports"]
    assert after == before + 1


def test_message_text_is_refused():
    client, _ = make()
    r = client.post("/v1/intel/reports", json={"kind": "report", "sender": "254700000803", "category": "x",
                                                "at": "2026-10-20T14:02:11Z", "text": "Nimekutumia 2,500 kimakosa"})
    assert r.status_code == 422


def test_reset_restores_the_seed():
    client, _ = make()
    client.post("/admin/numbers", json={"msisdn": "254700000805", "status": "blocked"})
    client.post("/admin/reset")
    assert "254700000805" not in {n["msisdn"] for n in client.get("/v1/intel/numbers").json()["numbers"]}
