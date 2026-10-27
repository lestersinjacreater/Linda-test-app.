"""The fraud-team console (contract 5.15): guarded, real phones only, a person can confirm or clear a number."""
from conftest import Radar

REAL = ["a1" * 8, "b2" * 8]
SIM = "5111" + "0" * 12
KEY = {"X-Fraud-Team-Key": "k3y"}


def console_radar(**kw):
    return Radar(demo_trusted_prefix="5111", fraud_team_console=True, fraud_team_key="k3y", **kw)


def test_console_is_off_unless_switched_on():
    radar = Radar()
    assert radar.client.get("/v1/fraud-team/console").status_code == 404
    assert radar.client.post("/v1/fraud-team/confirm", json={"msisdn": "254700000777"}).status_code == 404


def test_console_needs_the_key():
    radar = console_radar()
    assert radar.client.get("/v1/fraud-team/console").status_code == 403
    assert radar.client.get("/v1/fraud-team/console", headers={"X-Fraud-Team-Key": "wrong"}).status_code == 403
    assert radar.client.get("/v1/fraud-team/console", headers=KEY).status_code == 200


def test_queue_shows_real_reports_with_full_numbers_waiting_first():
    radar = console_radar()
    radar.report(sender="254700000777", device=REAL[0], category="sent_by_mistake")
    radar.report(sender="254700000777", device=REAL[1], category="sent_by_mistake")
    radar.report(sender="254700000555", device=REAL[0], category="prize")
    radar.report(sender="254700000999", device=SIM)  # the simulation: left out
    body = radar.client.get("/v1/fraud-team/console", headers=KEY).json()
    assert body["counts"] == {"reports": 3, "waiting": 2, "confirmed": 0, "phones": 2}
    numbers = {q["number"]: q for q in body["queue"]}
    assert set(numbers) == {"0700 000 777", "0700 000 555"}
    assert numbers["0700 000 777"]["reports"] == 2 and numbers["0700 000 777"]["phones"] == 2
    assert numbers["0700 000 777"]["status"] == "suspected" and numbers["0700 000 555"]["status"] == "reported"
    assert numbers["0700 000 555"]["new"] is True
    assert body["patterns"] == {"sent_by_mistake": 2, "prize": 1}
    assert [f["kind"] for f in body["feed"]] == ["report", "report", "report"]


def test_confirming_reaches_phones_the_network_and_the_feed():
    radar = console_radar()
    radar.report(sender="254700000777", device=REAL[0], category="fake_mpesa")
    r = radar.client.post("/v1/fraud-team/confirm", json={"msisdn": "254700000777"}, headers=KEY)
    assert r.status_code == 200 and r.json()["status"] == "confirmed"
    assert radar.risk("254700000777")["status"] == "confirmed"
    assert radar.notified == ["254700000777"]  # the network was told
    added = radar.client.get("/v1/sync", headers={"X-Linda-Device": REAL[1]}).json()["added"]
    assert len(added) == 1 and added[0]["category"] == "fake_mpesa"  # phones get it at their next sync
    body = radar.client.get("/v1/fraud-team/console", headers=KEY).json()
    assert body["counts"]["confirmed"] == 1 and body["counts"]["waiting"] == 0
    assert body["feed"][0]["kind"] == "confirmed"


def test_clearing_says_not_a_scam():
    radar = console_radar()
    radar.report(sender="254700000777", device=REAL[0])
    radar.client.post("/v1/fraud-team/confirm", json={"msisdn": "254700000777"}, headers=KEY)
    r = radar.client.post("/v1/fraud-team/clear", json={"msisdn": "254700000777"}, headers=KEY)
    assert r.status_code == 200
    assert radar.risk("254700000777")["status"] == "allowlisted"


def test_verified_and_unreported_numbers_cannot_be_confirmed():
    radar = console_radar()
    r = radar.client.post("/v1/fraud-team/confirm", json={"msisdn": "254722000000"}, headers=KEY)
    assert r.status_code == 409  # Safaricom Customer Care is never a scammer
    r = radar.client.post("/v1/fraud-team/confirm", json={"msisdn": "254700000123"}, headers=KEY)
    assert r.status_code == 409  # nobody reported it
    r = radar.client.post("/v1/fraud-team/confirm", json={"msisdn": "0700000123"}, headers=KEY)
    assert r.status_code == 422
