"""The Kenyan SMS builder: our labels file holds no message text, and the test split stays honest."""
import csv
import re

from src.features.dataset import kenya_sms as K

PERSONAL_NUMBER = re.compile(r"(?<!\d)(?:\+?254|0)[17]\d{8}(?!\d)")


def read_labels():
    with open(K.LABELS, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def test_labels_file_holds_fingerprints_and_labels_only():
    rows = read_labels()
    assert rows, "the labels file is empty"
    assert set(rows[0]) == {"raw_id", "label", "looks_generated"}  # no text column, ever
    for r in rows:
        assert re.fullmatch(r"[0-9a-f]{16}", r["raw_id"]), r
        assert r["label"] in K.VALID_LABELS, r
        assert r["looks_generated"] in ("true", "false"), r
    assert len({r["raw_id"] for r in rows}) == len(rows)


def test_raw_id_is_a_fingerprint_of_the_trimmed_message():
    assert K.raw_id("  Hello  ") == K.raw_id("Hello")
    assert len(K.raw_id("Hello")) == 16


def test_generated_looking_messages_never_reach_the_test_split():
    keys = [f"group {i}" for i in range(300)]
    assert all(K.split_for(k, True) in ("train", "val") for k in keys)
    assert {K.split_for(k, False) for k in keys} == {"train", "val", "test"}


def _write_raw(path, messages):
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["message_content", "prelabel", "label"])
        for m in messages:
            w.writerow([m, "0: Legit (Low-risk)", "legit"])


def test_build_uses_our_labels_scrubs_numbers_and_drops_unsure(tmp_path):
    msgs = {
        "scam": "Nimekutumia 2,500 kimakosa, nirudishie kwa 0712345678 haraka",
        "betting_ad": "Cheza Aviator leo! Bonus ya 50X kwa kila deposit www.example-bet.ke STOP *456*9*5#",
        "legit": "Niko kwa njia, nitafika saa nane",
        "unsure": "Tuma kwa hii namba",
    }
    raw = tmp_path / "raw.csv"
    _write_raw(raw, list(msgs.values()) + ["A message nobody labelled"])
    labels = tmp_path / "labels.csv"
    with open(labels, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["raw_id", "label", "looks_generated"])
        for label, m in msgs.items():
            w.writerow([K.raw_id(m), label, "false"])
    rows, stats = K.build(raw, labels)
    assert stats["unsure"] == 1 and stats["unlabelled"] == 1 and stats["kept"] == 3
    by_campaign = {r["campaign"]: r for r in rows}
    assert by_campaign["betting_ad"]["label"] == "legit"  # betting adverts are not scams
    assert by_campaign["betting_ad"]["is_hard_negative"] == "true"
    assert {r["label"] for r in rows} == {"scam", "legit"}
    assert "0712345678" not in by_campaign["sent_by_mistake"]["text"]  # the real number is replaced
    for r in rows:
        numbers = PERSONAL_NUMBER.findall(r["text"].replace(" ", ""))
        assert all(n == "0700000000" for n in numbers), r["text"]  # only the placeholder may remain
        assert r["source"] == "kenya_sms" and r["sender"] == ""


def test_missing_raw_file_gives_no_rows(tmp_path):
    rows, stats = K.build(tmp_path / "missing.csv")
    assert rows == [] and stats["read"] == 0
