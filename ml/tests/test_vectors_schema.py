"""Checks that shared/test-vectors.json is well formed and covers what the spec requires.

Both the Python and Kotlin tests run against this file, so a broken file breaks both
sides. This test guards the file itself, not the detector.
"""
import json
import re
from pathlib import Path

VECTORS_PATH = Path(__file__).resolve().parents[2] / "shared" / "test-vectors.json"
SENDERS_PATH = VECTORS_PATH.parent / "verified_senders.json"

LABELS = {"SAFE", "CAUTION", "SCAM"}
LANGUAGES = {"en", "sw", "sheng", "mixed"}
REQUIRED_KEYS = {
    "id", "sender", "text", "expected_label", "expected_normalized",
    "expected_score_min", "expected_score_max", "expected_fingerprint",
    "campaign", "language", "is_hard_negative", "tags",
}
REQUIRED_SCAM_CAMPAIGNS = {
    "reversal", "fake_mpesa", "prize", "fuliza_upgrade",
    "kra_refund", "job_fee", "pin_request", "phishing_link", "loan_fee",
}


def load_vectors():
    with open(VECTORS_PATH, encoding="utf-8") as f:
        return json.load(f)["vectors"]


def test_at_least_60_vectors():
    assert len(load_vectors()) >= 60


def test_ids_are_unique():
    ids = [v["id"] for v in load_vectors()]
    assert len(ids) == len(set(ids))


def test_every_vector_has_required_fields_and_valid_values():
    for v in load_vectors():
        missing = REQUIRED_KEYS - v.keys()
        assert not missing, f"{v.get('id')} is missing {missing}"
        assert v["expected_label"] in LABELS, v["id"]
        assert v["language"] in LANGUAGES, v["id"]
        assert isinstance(v["text"], str) and v["text"].strip(), v["id"]
        assert 0.0 <= v["expected_score_min"] <= v["expected_score_max"] <= 1.0, v["id"]
        assert re.fullmatch(r"[0-9a-f]{16}", v["expected_fingerprint"]), v["id"]
        assert isinstance(v["is_hard_negative"], bool), v["id"]
        assert isinstance(v["tags"], list), v["id"]


def test_all_scam_campaign_types_are_covered():
    covered = {v["campaign"] for v in load_vectors() if v["expected_label"] == "SCAM"}
    assert REQUIRED_SCAM_CAMPAIGNS <= covered, REQUIRED_SCAM_CAMPAIGNS - covered


def test_all_languages_are_covered():
    assert LANGUAGES <= {v["language"] for v in load_vectors()}


def test_fake_mpesa_from_personal_number_and_real_mpesa_pair_exist():
    vectors = load_vectors()
    fake = [v for v in vectors if v["campaign"] == "fake_mpesa" and v["sender"].startswith("2547")]
    real = [v for v in vectors if v["sender"] == "MPESA" and v["expected_label"] == "SAFE"]
    assert fake, "need a fake M-Pesa message from a personal 2547XXXXXXXX number"
    assert real, "need a real M-Pesa message from the MPESA sender ID"


def test_verified_senders_are_never_labelled_scam():
    verified = set(json.loads(SENDERS_PATH.read_text())["sender_ids"])
    for v in load_vectors():
        if v["sender"] in verified and v["campaign"] != "fake_mpesa":
            assert v["expected_label"] == "SAFE", v["id"]


def test_hard_negatives_cover_the_required_kinds():
    ids = " ".join(v["id"] for v in load_vectors() if v["is_hard_negative"])
    for kind in ("reversal", "fuliza", "otp", "kra", "delivery", "kplc"):
        assert kind in ids, f"no hard negative for {kind}"


def test_obfuscated_and_safe_chat_present():
    vectors = load_vectors()
    assert any("obfuscated" in v["tags"] for v in vectors)
    assert any(v["text"] == "Hey, are we still meeting at 6?" and v["expected_label"] == "SAFE" for v in vectors)


def test_only_placeholder_phone_numbers():
    # Every Kenyan number in the file must be in the fake 254700000xxx / 0700000xxx range.
    text = VECTORS_PATH.read_text(encoding="utf-8")
    for number in re.findall(r"\b(?:254|0)[17]\d{8}\b", text):
        assert number.startswith(("254700000", "070000")), f"non-placeholder phone number: {number}"
