"""The Python mirror of the app's inbox story (inbox_replay.py <-> InboxStory.kt). Same made-up texts as
android/.../InboxStoryTest.kt, so a pattern changed on one side only fails here or there."""
from datetime import datetime, timedelta

from src.features.evaluate import inbox_replay as R

NOW = datetime(2026, 10, 1)


def bonus(mb, link="https://safaricom.com/offers"):
    return f"Hongera! Umepata bonus ya {mb}MB kutoka Safaricom. Bonyeza {link} kuidai leo"


def story_with(n=6):
    s = R.Story()
    for i in range(n):
        s.remember("SAF_Info", "BUSINESS", bonus(100 * (i + 1)), NOW - timedelta(days=3 * (n - i)), reported=False)
    return s


def test_web_addresses_are_real_ones_only():
    assert R.hosts("See https://myaccount.safaricom.co.ke or www.bit.ly/x2") == {"myaccount.safaricom.co.ke", "bit.ly"}
    assert R.hosts("Confirmed.You have Ksh5.00.Amount") == set()


def test_a_routine_needs_five_texts_over_two_weeks():
    assert story_with().established("SAF_Info", "BUSINESS", NOW)
    assert not story_with(3).established("SAF_Info", "BUSINESS", NOW)
    assert not story_with().established("SAF_Info", "LOOKALIKE", NOW)


def test_routine_by_shape_or_known_address_and_a_new_address_is_new():
    s = story_with()
    assert s.routine("SAF_Info", bonus(500))
    assert s.routine("SAF_Info", "Your data plan expires tomorrow. Renew on https://safaricom.com/offers")
    assert R.hosts(bonus(500, "https://safaricom-bonus.xyz")) - s.sender("SAF_Info")["domains"]


def test_the_way_to_pay_has_changed():
    def changed(t):
        n = R.normalize(t)
        return bool(R.PAYMENT_CHANGED.search(n) and R.PAYMENT_PLACE.search(n))
    assert changed("Hello, kindly note our new till number for rent is 556677. Thank you.")
    assert changed("Habari, kodi sasa ilipwe kwa paybill mpya 400200, akaunti 12B.")
    assert not changed("Please pay to till 556677 as usual, thanks")
    assert not changed("My plans have changed, see you tomorrow")


def test_the_story_keeps_only_the_latest_shapes():
    s = R.Story()
    for i in range(40):
        s.remember("SAF_Info", "BUSINESS", bonus(i), NOW, reported=False)
    assert len(s.sender("SAF_Info")["templates"]) == R.MAX_TEMPLATES
    assert s.sender("SAF_Info")["count"] == 40
