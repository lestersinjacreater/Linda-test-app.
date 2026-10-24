"""Tests for the live phone's pure parts: reading /phone commands, who may drive the phone, the key gate.

Run: python -m pytest tests/virtual_phone -v
"""
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools" / "virtual-phone"))

from gate import key_ok  # noqa: E402
from listen import Session, is_trusted, parse_commands, valid_number  # noqa: E402
from phone import input_text_arg  # noqa: E402


# ---------- reading commands ----------
def test_each_phone_line_is_a_command_in_order():
    body = "Let's test\n/phone sms 0700000777 Nimekutumia pesa kimakosa\n  /PHONE tap See why \nthanks"
    assert parse_commands(body) == [("sms", "0700000777 Nimekutumia pesa kimakosa"), ("tap", "See why")]


def test_bare_phone_means_help_and_glued_words_are_not_commands():
    assert parse_commands("/phone") == [("help", "")]
    assert parse_commands("/phonecall 0700000777") == []
    assert parse_commands("see `/phone shot` above") == []  # only lines that START with /phone


def test_at_most_twenty_commands_per_comment():
    assert len(parse_commands("\n".join(["/phone back"] * 50))) == 20


# ---------- who may drive the phone (the repo is public) ----------
@pytest.mark.parametrize("assoc", ["OWNER", "MEMBER", "COLLABORATOR"])
def test_owner_and_collaborators_are_obeyed(assoc):
    assert is_trusted({"author_association": assoc, "user": {"login": "lester", "type": "User"}})


@pytest.mark.parametrize("assoc", ["NONE", "CONTRIBUTOR", "FIRST_TIMER", "FIRST_TIME_CONTRIBUTOR", None])
def test_strangers_are_ignored(assoc):
    assert not is_trusted({"author_association": assoc, "user": {"login": "someone", "type": "User"}})


def test_bots_are_ignored_even_our_own_replies():
    assert not is_trusted({"author_association": "OWNER", "user": {"login": "github-actions[bot]", "type": "Bot"}})


# ---------- inputs ----------
def test_phone_numbers():
    for ok in ("0700000777", "254700000777", "+254700000777", "333"):
        assert valid_number(ok)
    for bad in ("", "07 00", "0700000777; reboot", "abc", "1" * 16):
        assert not valid_number(bad)


def test_typed_text_cannot_escape_the_shell():
    assert input_text_arg("it's 6 pm") == "'it'\\''s%s6%spm'"
    assert input_text_arg("a; reboot") == "'a;%sreboot'"  # stays inside the quotes


class FakePhone:
    out = Path(".")

    def __init__(self):
        self.calls = []

    def adb(self, *a, **k):
        self.calls.append(("adb", a))
        return ""

    def shell(self, cmd, **k):
        self.calls.append(("shell", cmd))
        return ""

    def tap_exact(self, *a, **k):
        return False

    def tap_text(self, *a, **k):
        return False


def test_sms_reaches_the_emulator_as_one_line(monkeypatch):
    monkeypatch.setattr("listen.time.sleep", lambda s: None)
    p = FakePhone()
    Session(p, None, "app.apk").run("sms", "0700000777 Hongera!\n  Umeshinda Ksh 50,000")
    assert p.calls == [("adb", ("emu", "sms", "send", "0700000777", "Hongera! Umeshinda Ksh 50,000"))]


@pytest.mark.parametrize("cmd,args", [("sms", "0700000777"), ("sms", "notanumber hi"), ("call", "07x"),
                                      ("tap", "Nothing like this"), ("tapxy", "1 2 3"), ("scroll", "left"),
                                      ("reboot", ""), ("type", "")])
def test_wrong_commands_are_refused_without_touching_the_phone(cmd, args, monkeypatch):
    monkeypatch.setattr("listen.time.sleep", lambda s: None)
    p = FakePhone()
    with pytest.raises(ValueError):
        Session(p, None, "app.apk").run(cmd, args)
    assert not [c for c in p.calls if c[0] == "adb"]


def test_stop_ends_the_session():
    s = Session(FakePhone(), None, "app.apk")
    s.run("stop", "")
    assert s.stopped


# ---------- the key gate in front of the screen ----------
def test_gate_needs_the_key_as_the_first_part_of_the_path():
    key = "linda-mango-river-42"
    assert key_ok(b"GET /linda-mango-river-42/ HTTP/1.1\r\n", key)
    assert key_ok(b"GET /linda-mango-river-42/?action=multiplex HTTP/1.1\r\n", key)  # the live connection
    assert key_ok(b"GET /linda-mango-river-42 HTTP/1.1\r\n", key)
    for line in (b"GET / HTTP/1.1\r\n", b"GET /?action=multiplex HTTP/1.1\r\n", b"GET /linda-mango/ HTTP/1.1\r\n",
                 b"GET /linda-mango-river-42x/ HTTP/1.1\r\n", b"GET /x/linda-mango-river-42/ HTTP/1.1\r\n",
                 b"garbage\r\n", b""):
        assert not key_ok(line, key)


def test_gate_refuses_everything_without_a_key():
    assert not key_ok(b"GET // HTTP/1.1\r\n", "")
