"""Contract tests for the endpoints in root CLAUDE.md 5.1, 5.2, 5.3 and for privacy and abuse limits."""
from tests.conftest import Radar, device


def test_report_response_shape(radar):
    r = radar.report()
    assert r.status_code == 200
    assert set(r.json()) == {"status", "devices"}
    assert r.json() == {"status": "unknown", "devices": 1}


def test_message_text_is_rejected_not_stored(radar):
    for field in ("text", "body", "message"):
        r = radar.report(**{field: "QK7RT2XY9P Confirmed..."})
        assert r.status_code == 422, field


def test_invalid_reports_are_rejected(radar):
    assert radar.report(sender="0712345678").status_code == 422        # wrong number format
    assert radar.report(category="made_up").status_code == 422
    assert radar.report(confidence=1.5).status_code == 422
    assert radar.report(fingerprint="xyz").status_code == 422
    assert radar.report(device="not hex!!").status_code == 422


def test_verified_senders_are_never_reported_or_confirmed(radar):
    for n in range(1, 6):  # five fully trusted devices, created by an ordinary report
        radar.report(sender="254700000199", device=device(n))
        radar.set_trust(device(n), 1.0)
    for sender in ("MPESA", "mpesa", "M-PESA", "SAFARICOM", "KPLC", "EQUITY"):
        for n in range(1, 6):
            body = radar.report(sender=sender, device=device(n), confidence=1.0).json()
        assert body["status"] == "allowlisted", sender
    assert radar.notified == ["254700000199"]  # only the ordinary setup number, never a verified sender
    from src.core.db import Report
    with radar.app.state.sessions() as s:
        assert s.query(Report).filter(Report.sender.in_(["MPESA", "mpesa", "KPLC"])).count() == 0  # not even stored
    assert radar.risk("MPESA")["status"] == "allowlisted"
    added = [e["msisdn"] for e in radar.client.get("/v1/blocklist").json()["added"]]
    assert added == ["254700000199"]


def test_per_device_rate_limit(radar):
    limit = radar.settings.max_reports_per_device_per_hour
    for i in range(limit):
        assert radar.report(sender=f"2547000{i:05d}").status_code == 200
    assert radar.report(sender="254700099999").status_code == 429
    radar.clock.advance(3601)
    assert radar.report(sender="254700099999").status_code == 200


def test_risk_of_unknown_number(radar):
    assert radar.risk("254700000123") == {
        "msisdn": "254700000123", "status": "unknown", "category": None, "reports": 0, "confirmed_at": None,
    }
    assert radar.client.get("/v1/numbers/12345/risk").status_code == 422


def test_blocklist_added_and_removed(radar):
    sender = "254700000050"
    for n in (1, 2, 3):
        radar.report(device=device(n))
        radar.set_trust(device(n), 0.9)
        radar.report(device=device(n))
    full = radar.client.get("/v1/blocklist").json()
    assert full["added"] == [{"msisdn": sender, "category": "fake_mpesa"}]
    assert full["removed"] == []
    as_of = full["as_of"]

    radar.clock.advance(60)
    nothing_new = radar.client.get("/v1/blocklist", params={"since": as_of}).json()
    assert nothing_new["added"] == [] and nothing_new["removed"] == []

    from src.features.confirmation.service import allowlist_sender
    with radar.app.state.sessions() as s:
        allowlist_sender(s, sender, radar.clock())
    later = radar.client.get("/v1/blocklist", params={"since": as_of}).json()
    assert later["removed"] == [sender]
    assert radar.risk(sender)["status"] == "allowlisted"


def test_telco_notifier_retries_then_gives_up():
    import httpx
    from src.core.config import Settings
    from src.features.confirmation.notifier import make_telco_notifier

    calls, sleeps = [], []
    def fake_post(url, json, timeout):
        calls.append((url, json))
        raise httpx.ConnectError("telco down")

    import src.features.confirmation.notifier as mod
    original = mod.httpx.post
    mod.httpx.post = fake_post
    try:
        make_telco_notifier(Settings(telco_url="http://telco:8000"), sleep=sleeps.append)("254700000050")
    finally:
        mod.httpx.post = original
    assert len(calls) == 3
    assert calls[0] == ("http://telco:8000/network/confirm", {"sender": "254700000050"})
    assert sleeps == [1.0, 2.0]  # waits grow, no wait after the last attempt
