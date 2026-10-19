"""Verified callers (root CLAUDE.md 5.2 and 5.13): genuine numbers get a badge and can never be flagged."""
from tests.conftest import Radar

SAFARICOM_CARE = "254722000000"


def test_the_list_is_served_for_the_phone():
    body = Radar().client.get("/v1/verified-callers").json()
    assert body["version"]
    care = {c["msisdn"]: c for c in body["callers"]}[SAFARICOM_CARE]
    assert care["name_en"] == "Safaricom Customer Care" and care["kind"] == "safaricom" and care["name_sw"]


def test_risk_lookup_says_verified_with_the_name():
    r = Radar().risk(SAFARICOM_CARE)
    assert r["status"] == "verified" and r["name_en"] == "Safaricom Customer Care" and r["reports"] == 0


def test_a_verified_caller_can_never_be_confirmed_even_by_trusted_phones():
    radar = Radar()
    for d in ("a", "b", "c", "d"):
        device = d * 16
        radar.report(sender="254700000050", device=device)  # create the device
        radar.set_trust(device, 1.0)
        assert radar.report(sender=SAFARICOM_CARE, device=device, confidence=0.99).json()["status"] == "allowlisted"
    assert radar.risk(SAFARICOM_CARE)["status"] == "verified"
    assert SAFARICOM_CARE not in radar.notified
    blocked = radar.client.get("/v1/blocklist").json()
    assert SAFARICOM_CARE not in [a["msisdn"] for a in blocked["added"]]


def test_an_ordinary_number_is_not_verified():
    assert Radar().risk("254700000050")["status"] == "unknown"
