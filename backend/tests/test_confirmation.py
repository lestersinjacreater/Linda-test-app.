"""The confirmation rule and its attack tests (backend/CLAUDE.md "Tests that must exist")."""
from tests.conftest import Radar, device

SENDER = "254700000050"


def trusted_report(radar: Radar, n: int, trust: float = 0.9, **kw):
    """Report from device n after first making it an established device."""
    radar.report(device=device(n), sender=kw.pop("sender", SENDER), **kw)  # creates the device
    radar.set_trust(device(n), trust)
    return radar.report(device=device(n), sender=SENDER, **kw)


def test_three_independent_trusted_devices_confirm(radar):
    for n in (1, 2):
        assert trusted_report(radar, n).json()["status"] != "confirmed"
    body = trusted_report(radar, 3).json()
    assert body == {"status": "confirmed", "devices": 3}
    assert radar.risk(SENDER)["status"] == "confirmed"
    assert radar.risk(SENDER)["category"] == "fake_mpesa"
    assert radar.notified == [SENDER]  # the network was told, exactly once


def test_one_device_reporting_ten_times_does_not_confirm(radar):
    radar.report(device=device(1))
    radar.set_trust(device(1), 1.0)
    for _ in range(10):
        body = radar.report(device=device(1)).json()
    assert body["devices"] == 1
    assert body["status"] != "confirmed"
    assert radar.notified == []


def test_two_devices_are_not_enough_even_with_full_trust(radar):
    for n in (1, 2):
        trusted_report(radar, n, trust=1.0, confidence=1.0)
    assert radar.risk(SENDER)["status"] != "confirmed"


def test_three_new_devices_without_history_do_not_confirm(radar):
    for n in (1, 2, 3):
        body = radar.report(device=device(n), confidence=1.0).json()
    assert body["devices"] == 3
    assert body["status"] != "confirmed"


def test_reports_outside_the_time_window_do_not_count(radar):
    trusted_report(radar, 1)
    trusted_report(radar, 2)
    radar.clock.advance(radar.settings.window_s + 1)
    body = trusted_report(radar, 3).json()
    assert body["devices"] == 1
    assert body["status"] != "confirmed"


def test_poisoning_20_brand_new_devices_cannot_confirm_and_is_logged(radar):
    victim = "254700000099"  # an innocent number
    for n in range(100, 120):
        body = radar.report(sender=victim, device=device(n), confidence=1.0).json()
        radar.clock.advance(2)  # all within about 40 seconds
    assert body["devices"] == 20
    assert body["status"] != "confirmed"
    assert radar.risk(victim)["status"] != "confirmed"
    assert radar.notified == []
    from src.core.db import SecurityEvent
    with radar.app.state.sessions() as s:
        events = s.query(SecurityEvent).all()
    assert [e.kind for e in events] == ["poison_burst"]  # logged once, not once per report
    assert events[0].sender == victim


def test_trust_rises_after_a_confirmation_and_falls_after_silence(radar):
    for n in (1, 2, 3):
        trusted_report(radar, n, trust=0.9)
    from src.core.db import Device
    with radar.app.state.sessions() as s:
        assert s.get(Device, device(1)).trust > 0.9  # rewarded

    # A different device reports a sender nobody else backs; after the window its trust drops.
    radar.report(sender="254700000077", device=device(9))
    radar.set_trust(device(9), 0.8)
    radar.clock.advance(radar.settings.window_s + 1)
    radar.report(sender="254700000078", device=device(8))  # any report triggers settlement
    with radar.app.state.sessions() as s:
        assert s.get(Device, device(9)).trust < 0.8


def test_devices_without_integrity_token_are_capped_at_the_spec_trust():
    radar = Radar()  # spec default: cap 0.3
    for n in (1, 2, 3, 4, 5):
        radar.report(device=device(n), confidence=1.0)
        radar.set_trust(device(n), 1.0, token=False)  # even if their trust were high
        body = radar.report(device=device(n), confidence=1.0).json()
    assert body["status"] != "confirmed"


def test_fingerprint_of_a_confirmed_campaign_makes_a_new_number_suspected_not_confirmed(radar):
    for n in (1, 2, 3):
        trusted_report(radar, n)
    new_number = "254700000060"
    near = "426183190df2b093"  # 1 bit away from the campaign fingerprint
    body = radar.report(sender=new_number, device=device(40), fingerprint=near).json()
    assert body["status"] == "suspected"
    far = "bd9e7ce6f20d4d4d"
    assert radar.report(sender="254700000061", device=device(41), fingerprint=far).json()["status"] == "unknown"


def test_two_devices_agreeing_makes_a_sender_suspected(radar):
    radar.report(device=device(1))
    assert radar.report(device=device(1)).json()["status"] == "unknown"  # one phone only
    assert radar.report(device=device(2)).json()["status"] == "suspected"
