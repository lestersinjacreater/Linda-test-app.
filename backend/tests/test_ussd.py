"""USSD checker (root CLAUDE.md 5.6): a basic phone dials the code and checks a number before paying.

Africa's Talking posts form fields; `text` is every answer so far joined by "*". The reply starts with
"CON " (the menu goes on) or "END " (the session ends).
"""
from tests.conftest import Radar, device

DIALLER = "+254711000001"


def dial(radar: Radar, text: str, phone: str = DIALLER):
    return radar.client.post("/ussd", data={"sessionId": "s1", "serviceCode": "*384*123#", "phoneNumber": phone,
                                            "networkCode": "63902", "text": text})


def confirm(radar: Radar, sender: str) -> None:
    """Three established devices report the sender, so the gateway confirms it."""
    for n in range(1, 4):
        radar.report(sender="254700000199", device=device(n))
        radar.set_trust(device(n), 1.0)
    for n in range(1, 4):
        radar.report(sender=sender, device=device(n), confidence=0.99)
    assert radar.risk(sender)["status"] == "confirmed"


def test_the_first_screen_is_the_menu_in_both_languages():
    r = dial(Radar(), "")
    assert r.status_code == 200 and r.headers["content-type"].startswith("text/plain")
    assert r.text.startswith("CON ")
    assert "1. Check a number" in r.text and "2. How to stay safe" in r.text and "3. Kiswahili" in r.text


def test_check_asks_for_the_number():
    assert dial(Radar(), "1").text.startswith("CON Enter the number")


def test_a_confirmed_number_gets_a_warning_with_the_next_step():
    radar = Radar()
    confirm(radar, "254700000050")
    r = dial(radar, "1*0700 000 050").text
    assert r.startswith("END ") and "scam" in r.lower() and "0700 000 050" in r and "Do not send money" in r


def test_an_unknown_number_is_not_called_safe():
    r = dial(Radar(), "1*0700000051").text
    assert r.startswith("END ") and "No reports" in r and "safe" not in r.lower().replace("stay safe", "")


def test_a_verified_number_says_who_it_is():
    r = dial(Radar(), "1*0722000000").text
    assert r.startswith("END ") and "Safaricom Customer Care" in r and "Verified" in r


def test_a_number_the_fraud_team_reported_is_a_caution():
    radar = Radar()
    from src.core.db import IntelNumber
    with radar.app.state.sessions() as s:
        s.add(IntelNumber(msisdn="254700000803", status="reported", category="pin_request", reports=2,
                          campaign_id=None, synced_at=radar.clock()))
        s.commit()
    r = dial(radar, "1*+254700000803").text
    assert r.startswith("END ") and "reported" in r.lower() and "2" in r


def test_a_wrong_number_is_explained():
    r = dial(Radar(), "1*12345").text
    assert r.startswith("END ") and "07" in r


def test_how_to_stay_safe():
    r = dial(Radar(), "2").text
    assert r.startswith("END ") and "PIN" in r and "333" in r


def test_kiswahili_menu_and_answers():
    radar = Radar()
    assert dial(radar, "3").text.startswith("CON ") and "Angalia namba" in dial(radar, "3").text
    assert dial(radar, "3*1").text.startswith("CON Weka namba")
    assert "PIN" in dial(radar, "3*2").text and dial(radar, "3*2").text.startswith("END ")
    assert "Hakuna ripoti" in dial(radar, "3*1*0700000051").text


def test_replies_fit_one_ussd_screen():
    radar = Radar()
    confirm(radar, "254700000050")
    for text in ("", "1", "2", "3", "3*1", "3*2", "1*0700000050", "1*0700000051", "1*0722000000", "3*1*0700000050"):
        assert len(dial(radar, text).text) <= 182, text


def test_one_phone_cannot_scan_the_whole_list():
    radar = Radar(lookup_rate_per_hour=3)
    for _ in range(3):
        assert dial(radar, "1*0700000051").text.startswith("END No reports")
    assert "Too many" in dial(radar, "1*0700000051").text
    assert dial(radar, "1*0700000051", phone="+254711000002").text.startswith("END No reports")


def test_an_unknown_choice_shows_the_menu_again():
    assert dial(Radar(), "9").text.startswith("CON ")
