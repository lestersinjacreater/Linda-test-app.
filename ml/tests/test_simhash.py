import json
from pathlib import Path

import pytest

from src.features.fingerprint.simhash import char_ngrams, fnv1a_64, hamming_distance, simhash64

VECTORS = json.loads((Path(__file__).resolve().parents[2] / "shared" / "test-vectors.json").read_text(encoding="utf-8"))["vectors"]


@pytest.mark.parametrize("v", VECTORS, ids=[v["id"] for v in VECTORS])
def test_matches_shared_vectors(v):
    assert simhash64(v["expected_normalized"]) == v["expected_fingerprint"]


def test_fnv1a_known_values():
    # Published FNV-1a 64-bit test values; the Kotlin version must reproduce them.
    assert fnv1a_64(b"") == 0xCBF29CE484222325
    assert fnv1a_64(b"a") == 0xAF63DC4C8601EC8C
    assert fnv1a_64(b"foobar") == 0x85944171F73967E8


def test_ngrams_edge_cases():
    assert char_ngrams("") == []
    assert char_ngrams("ab") == ["ab"]
    assert char_ngrams("abcd") == ["abc", "bcd"]
    assert simhash64("") == "0000000000000000"


def test_same_text_same_fingerprint_and_output_is_16_hex():
    fp = simhash64("nimekutumia 2500 kimakosa")
    assert fp == simhash64("nimekutumia 2500 kimakosa")
    assert len(fp) == 16


def test_campaign_variants_are_close_and_unrelated_text_is_far():
    by_id = {v["id"]: v["expected_fingerprint"] for v in VECTORS}
    # Same fake M-Pesa campaign, different sender: identical text, distance 0.
    assert hamming_distance(by_id["fake-mpesa-01"], by_id["fake-mpesa-spoofsender-01"]) == 0
    # Obfuscated variant of the same text must stay close once normalised.
    assert hamming_distance(by_id["reversal-sw-01"], by_id["reversal-obf-leet-01"]) == 0
    # Ordinary chat is far from a scam.
    assert hamming_distance(by_id["reversal-sw-01"], by_id["chat-en-01"]) > 12
