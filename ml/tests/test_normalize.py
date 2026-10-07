import json
from pathlib import Path

import pytest

from src.features.normalize.normalize import normalize

VECTORS = json.loads((Path(__file__).resolve().parents[2] / "shared" / "test-vectors.json").read_text(encoding="utf-8"))["vectors"]


@pytest.mark.parametrize("v", VECTORS, ids=[v["id"] for v in VECTORS])
def test_matches_shared_vectors(v):
    assert normalize(v["text"]) == v["expected_normalized"]


@pytest.mark.parametrize("raw, expected", [
    ("K1m@kosa", "kimakosa"),
    ("M-P3SA", "mpesa"),
    ("m pesa", "mpesa"),
    ("M.PESA", "mpesa"),
    ("t u m a leo", "tuma leo"),
    ("p@$$w0rd", "password"),
    ("zawadi yak0.", "zawadi yako"),
    ("tum4 sasa", "tuma sasa"),
    ("😀 Hi!!", "hi"),
    ("", ""),
])
def test_disguises_are_undone(raw, expected):
    assert normalize(raw) == expected


@pytest.mark.parametrize("raw, expected", [
    ("2,500", "2500"),
    ("Ksh 2,500.00", "ksh 2500 00"),
    ("Ksh3,140.00", "ksh3140 00"),     # the 3 touches a letter but is part of the amount
    ("Ksh500", "ksh500"),
    ("Ksh3.50 only", "ksh3 50 only"),
    ("OTP is 482913", "otp is 482913"),
])
def test_amounts_and_numbers_survive(raw, expected):
    assert normalize(raw) == expected


def test_transaction_code_digits_are_not_treated_as_leetspeak():
    assert normalize("SJ34K9L2QW Confirmed") == "sj34k9l2qw confirmed"
    assert normalize("RK81MN3PQ4 Confirmed") == "rk81mn3pq4 confirmed"

