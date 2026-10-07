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


def test_untokened_devices_can_earn_trust_only_through_confirmed_reports():
    radar = Radar()  # default: no ceiling below 1.0 for untokened devices
    newcomer = device(9)
    radar.report(device=newcomer, confidence=1.0)
    from src.core.db import Device
    with radar.app.state.sessions() as s:
        assert s.get(Device, newcomer).trust == radar.settings.new_device_trust  # starts low
    # Three established devices (earned trust, no tokens) back the same sender; the newcomer joins them.
    for n in (1, 2, 3):
        radar.report(device=device(n), confidence=1.0)
        radar.set_trust(device(n), 0.9, token=False)
        radar.report(device=device(n), confidence=1.0)
    assert radar.risk(SENDER)["status"] == "confirmed"
    with radar.app.state.sessions() as s:
        assert s.get(Device, newcomer).trust > radar.settings.new_device_trust  # earned by a confirmed report
        assert s.get(Device, device(1)).trust > 0.9


def test_untokened_devices_stay_unproven_without_a_confirmation():
    radar = Radar()
    for n in (1, 2, 3, 4, 5):
        radar.report(device=device(n), confidence=1.0)
        radar.report(device=device(n), confidence=1.0)
    assert radar.risk(SENDER)["status"] != "confirmed"
    from src.core.db import Device
    with radar.app.state.sessions() as s:
        assert all(s.get(Device, device(n)).trust == radar.settings.new_device_trust for n in range(1, 6))


def test_the_literal_spec_cap_of_0_3_makes_confirmation_impossible_without_tokens():
    radar = Radar(no_token_trust_cap=0.3)  # kept as a setting so the old behaviour can be reproduced
    for n in (1, 2, 3, 4, 5):
        radar.report(device=device(n), confidence=1.0)
        radar.set_trust(device(n), 1.0, token=False)
        body = radar.report(device=device(n), confidence=1.0).json()
    assert body["status"] != "confirmed"


def test_with_a_token_the_device_is_not_held_to_the_cap():
    radar = Radar(no_token_trust_cap=0.3)
    for n in (1, 2, 3):
        radar.report(device=device(n), confidence=1.0, token="play-integrity-token")
        radar.set_trust(device(n), 0.9, token=True)
        body = radar.report(device=device(n), confidence=1.0, token="play-integrity-token").json()
    assert body["status"] == "confirmed"


def test_demo_trusted_prefix_gives_a_starting_trust_and_is_off_by_default():
    from src.core.db import Device
    on = Radar(demo_trusted_prefix="5111", demo_trusted_trust=0.8)
    on.report(device="5111" + "0" * 12)
    on.report(device="7777" + "0" * 12)
    with on.app.state.sessions() as s:
        assert s.get(Device, "5111" + "0" * 12).trust == 0.8
        assert s.get(Device, "7777" + "0" * 12).trust == 0.3
    off = Radar()
    off.report(device="5111" + "0" * 12)
    with off.app.state.sessions() as s:
        assert s.get(Device, "5111" + "0" * 12).trust == 0.3


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
