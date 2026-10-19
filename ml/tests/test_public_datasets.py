"""The public-dataset loader: cleaning, label mapping and leak-free splits. Uses tiny fixture files."""
import csv

from src.features.dataset.public import NAME_PLACEHOLDER, PHONE_PLACEHOLDER, build, clean


def _write(path, header, rows):
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(header)
        w.writerows(rows)


def test_clean_removes_numbers_names_and_tanzanian_details():
    out = clean("Iyo pesa itume kwenye HALOTEL 0787-406-889 jina lije ABDALLAH MWANAKU Tsh 5000")
    assert "0787" not in out and "ABDALLAH" not in out and "HALOTEL" not in out.upper()
    assert PHONE_PLACEHOLDER in out and NAME_PLACEHOLDER in out and "Ksh" in out


def test_clean_masks_signed_names_and_international_numbers():
    out = clean("Call 09061701461 now. By JUMA SIKAREKO")
    assert "09061701461" not in out and "JUMA" not in out


def test_labels_are_mapped_and_adverts_are_dropped(tmp_path):
    _write(tmp_path / "bongoscam.csv", ["Category", "Sms"], [
        ["scam", "Naomba unitumie iyo hela kwenye namba hii 0689933027"],
        ["trust", "Nipigie baada ya saa moja tafadhali"],
    ])
    _write(tmp_path / "mendeley_smishing.csv", ["LABEL", "TEXT", "URL", "EMAIL", "PHONE"], [
        ["Smishing", "Your bank account is locked, verify at http://bank-verify.xyz", "Yes", "No", "No"],
        ["ham", "Are we still meeting at six this evening?", "No", "No", "No"],
        ["spam", "Get 50% off all shoes this weekend only", "No", "No", "No"],
    ])
    rows, stats = build(tmp_path)
    labels = {r["text"][:10]: r["label"] for r in rows}
    assert len(rows) == 4 and stats["read"] == 4  # the advert never enters
    assert sorted(labels.values()) == ["legit", "legit", "scam", "scam"]
    assert all(r["sender"] == "" and r["is_hard_negative"] == "false" for r in rows)


def test_near_duplicates_never_straddle_splits(tmp_path):
    variants = [[ "scam", f"Iyo pesa itume kwenye namba hii {n} jina lije JOHN DOE asante sana"] for n in
                ("0689933027", "0711222333", "0787406889", "0655111222", "0699000111")]
    others = [["trust", f"Habari ya {w} rafiki yangu tutaonana kesho"] for w in
              ("asubuhi", "mchana", "jioni", "usiku", "leo", "wiki", "mwezi", "mwaka")]
    _write(tmp_path / "bongoscam.csv", ["Category", "Sms"], variants + others)
    rows, _ = build(tmp_path)
    scam_splits = {r["split"] for r in rows if r["label"] == "scam"}
    assert len(scam_splits) == 1  # all variants of the one script land in the same split


def test_missing_files_are_skipped(tmp_path):
    rows, stats = build(tmp_path)
    assert rows == [] and stats["read"] == 0
