"""GET /v1/stats (contract 5.14): real counts only, simulated phones left out, numbers masked."""
from src.features.stats.service import mask

from conftest import Radar

REAL = ["a1" * 8, "b2" * 8, "c3" * 8]
SIM = "5111" + "0" * 12


def test_empty_server_has_all_zero_counts():
    body = Radar(demo_trusted_prefix="5111").client.get("/v1/stats").json()
    assert body["phones"] == {"total": 0, "active_24h": 0}
    assert body["scams_caught"] == {"total": 0, "last_24h": 0}
    assert body["numbers"] == {"confirmed": 0, "suspected": 0}
    assert body["by_category"] == {} and body["recent"] == []


def test_phones_checking_in_are_counted_and_age_out_of_active():
    radar = Radar(demo_trusted_prefix="5111")
    radar.client.get("/v1/sync", headers={"X-Linda-Device": REAL[0]})
    radar.client.get("/v1/lookup?prefix=abcde", headers={"X-Linda-Device": REAL[1]})
    radar.client.get("/v1/sync", headers={"X-Linda-Device": REAL[0]})  # same phone again: still one
    assert radar.client.get("/v1/stats").json()["phones"] == {"total": 2, "active_24h": 2}
    radar.clock.advance(2 * 86400)
    radar.client.get("/v1/sync", headers={"X-Linda-Device": REAL[1]})
    assert radar.client.get("/v1/stats").json()["phones"] == {"total": 2, "active_24h": 1}


def test_simulated_and_junk_device_ids_are_not_counted():
    radar = Radar(demo_trusted_prefix="5111")
    radar.client.get("/v1/sync", headers={"X-Linda-Device": SIM})
    radar.client.get("/v1/sync", headers={"X-Linda-Device": "not-a-device <script>"})
    radar.client.get("/v1/sync")  # no header at all
    radar.report(device=SIM)
    body = radar.client.get("/v1/stats").json()
    assert body["phones"]["total"] == 0
    assert body["scams_caught"]["total"] == 0 and body["recent"] == []


def test_real_reports_count_by_type_and_confirmations_show_masked():
    radar = Radar(demo_trusted_prefix="5111")
    radar.report(sender="254700000888", device=REAL[0], category="prize")
    for d in REAL[1:]:
        radar.report(sender="254700000888", device=d, category="prize")
    for d in REAL:
        radar.set_trust(d, 0.9)  # established phones, as in test_confirmation
    radar.clock.advance(60)
    for d in REAL:
        radar.report(sender="254700000777", device=d, confidence=0.95)
    body = radar.client.get("/v1/stats").json()
    assert body["phones"]["total"] == 3  # reporting counts as checking in
    assert body["scams_caught"] == {"total": 6, "last_24h": 6}
    assert body["by_category"] == {"fake_mpesa": 3, "prize": 3}
    assert body["numbers"]["confirmed"] == 1
    assert body["recent"][0]["category"] == "fake_mpesa"  # newest first
    assert {r["number"] for r in body["recent"]} == {"0700 ••• 777", "0700 ••• 888"}
    assert "254700000777" not in str(body) and "254700000888" not in str(body)  # never a full number
    statuses = {r["number"]: r["status"] for r in body["recent"]}
    assert statuses["0700 ••• 777"] == "confirmed"


def test_numbers_confirmed_only_by_simulated_phones_are_not_counted():
    radar = Radar(demo_trusted_prefix="5111", demo_trusted_trust=1.0)
    for i in range(3):
        radar.report(sender="254700000999", device="5111" + str(i) * 12, confidence=0.95)
    assert radar.risk("254700000999")["status"] == "confirmed"  # the simulation did confirm it...
    assert radar.client.get("/v1/stats").json()["numbers"]["confirmed"] == 0  # ...but it is not real


def test_mask():
    assert mask("254712345678") == "0712 ••• 678"
    assert mask("MPESA") == "•••"
