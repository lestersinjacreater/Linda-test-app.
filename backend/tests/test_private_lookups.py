"""Private lookups (5.10) and the hashed sync (5.11): useful to phones, useless for harvesting the list."""
import json
from pathlib import Path

from src.features.private.hashing import number_hash
from tests.conftest import Radar
from tests.test_intel import FakeIntel, Gateway

VECTORS = json.loads((Path(__file__).resolve().parents[2] / "shared" / "hash-vectors.json").read_text())


def confirm(radar, sender="254700000050"):
    for d in ("a", "b", "c"):
        device = d * 16
        radar.report(sender="254700000099", device=device)  # create the device
        radar.set_trust(device, 1.0)
    for d in ("a", "b", "c"):
        radar.report(sender=sender, device=d * 16)
    assert radar.risk(sender)["status"] == "confirmed"


def lookup(client, msisdn, device="d" * 16):
    return client.get("/v1/lookup", params={"prefix": number_hash(msisdn)[:5]}, headers={"X-Linda-Device": device})


def test_hashes_match_the_shared_vectors():
    for case in VECTORS["cases"]:
        assert number_hash(case["msisdn"]) == case["sha256"]


def test_a_confirmed_number_is_found_by_its_prefix_with_a_plain_label_and_no_full_number():
    radar = Radar()
    confirm(radar)
    body = lookup(radar.client, "254700000050").json()
    entry = next(e for e in body["entries"] if e["hash"] == number_hash("254700000050"))
    assert entry["status"] == "confirmed" and entry["category"] == "fake_mpesa"
    assert entry["label"].startswith("Reported for fraud by") and entry["label_sw"]
    assert "254700000050" not in json.dumps(body)  # only hashes travel


def test_only_entries_with_that_prefix_come_back():
    radar = Radar()
    confirm(radar, "254700000050")
    confirm(radar, "254700000051")
    prefix = number_hash("254700000050")[:5]
    entries = radar.client.get("/v1/lookup", params={"prefix": prefix}).json()["entries"]
    assert entries and all(e["hash"].startswith(prefix) for e in entries)  # nothing outside the bucket leaks


def test_verified_callers_come_back_verified():
    radar = Radar()
    entry = next(e for e in lookup(radar.client, "254722000000").json()["entries"] if e["hash"] == number_hash("254722000000"))
    assert entry == {"hash": number_hash("254722000000"), "status": "verified", "category": None,
                     "label": "Safaricom Customer Care", "label_sw": "Huduma kwa Wateja ya Safaricom"}


def test_numbers_only_reported_to_safaricom_are_returned_as_reported():
    g = Gateway(FakeIntel())
    g.sync()
    entry = next(e for e in lookup(g.client, "254700000803").json()["entries"] if e["hash"] == number_hash("254700000803"))
    assert entry["status"] == "reported" and entry["category"] == "pin_request"
    assert entry["label"] == "Reported to Safaricom by 2 people"


def test_labels_never_carry_evidence():
    g = Gateway(FakeIntel())
    g.sync()
    for e in lookup(g.client, "254700000777").json()["entries"]:
        assert set(e) == {"hash", "status", "category", "label", "label_sw"}


def test_a_bad_prefix_is_refused():
    client = Radar().client
    assert client.get("/v1/lookup", params={"prefix": "a3f"}).status_code == 422          # too short: too much
    assert client.get("/v1/lookup", params={"prefix": "a3f09c1"}).status_code == 422      # too long: too revealing
    assert client.get("/v1/lookup", params={"prefix": "zzzzz"}).status_code == 422


def test_lookups_are_rate_limited_per_device():
    radar = Radar(lookup_rate_per_hour=3)
    codes = [lookup(radar.client, "254700000050", device="x" * 16).status_code for _ in range(4)]
    assert codes == [200, 200, 200, 429]
    assert lookup(radar.client, "254700000050", device="y" * 16).status_code == 200  # another phone is fine
    radar.clock.advance(3601)
    assert lookup(radar.client, "254700000050", device="x" * 16).status_code == 200


def test_sync_sends_hashes_of_recent_confirmed_numbers_only():
    radar = Radar()
    confirm(radar)
    body = radar.client.get("/v1/sync").json()
    assert [a["hash"] for a in body["added"]] == [number_hash("254700000050")]
    assert body["added"][0]["status"] == "confirmed" and "254700000050" not in json.dumps(body)
    assert body["removed"] == []


def test_sync_since_returns_only_changes():
    radar = Radar()
    confirm(radar)
    as_of = radar.client.get("/v1/sync").json()["as_of"]
    radar.clock.advance(10)
    assert radar.client.get("/v1/sync", params={"since": as_of}).json()["added"] == []


def test_old_confirmations_age_out_of_the_offline_list():
    radar = Radar(sync_recent_days=30)
    confirm(radar)
    as_of = radar.client.get("/v1/sync").json()["as_of"]
    radar.clock.advance(31 * 86400)
    body = radar.client.get("/v1/sync", params={"since": as_of}).json()
    assert body["added"] == [] and body["removed"] == [number_hash("254700000050")]
    assert radar.client.get("/v1/sync").json()["added"] == []  # a fresh phone never gets it


def test_reported_and_suspected_numbers_never_reach_the_offline_list():
    g = Gateway(FakeIntel())
    g.sync()
    hashes = {a["hash"] for a in g.client.get("/v1/sync").json()["added"]}
    assert number_hash("254700000803") not in hashes  # only reported to 333
    assert number_hash("254722000000") not in hashes  # verified, whatever the feed said
    assert number_hash("254700000777") in hashes       # blocked by the fraud team
