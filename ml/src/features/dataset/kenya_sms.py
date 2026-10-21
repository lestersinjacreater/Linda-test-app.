"""Turns the Kenyan SMS dataset into rows the trainer understands, using OUR reviewed labels.

Source (download it yourself, put it in ml/data/raw/, never commit it):
  - kenya_sms_mwatsefu.csv  data/raw/scam_detection_labeled.csv from github.com/Salimmwatsefu/NLP-Scam-Detection
                            (Salim Mwatsefu, IwazoLab), used with his permission. Columns: message_content, prelabel, label

Why we do not use the file's own labels: most of them come from a keyword script (any message with "now" or
"today" is a scam, any message with "mpesa" or "rent" is genuine). Claude re-read every message and labelled it again
(ml/data/labels/kenya_sms_labels.csv, reviewed 21 Oct 2026; the team spot-checks a sample). That file holds only a
fingerprint of each raw message (first 16 hex characters of its SHA-256) and the label, never the text.

Labels: scam, legit, betting_ad, unsure.
  - betting_ad: a gambling advert that does not pretend money was already sent to you. Linda should not warn on it, so
    it is trained as legit, but it keeps its own campaign name so the metrics can report it separately.
  - unsure: left out of training and testing.
  - looks_generated: short, generic messages that look written for the dataset rather than received. They may be used
    for training but never for testing, so the Kenyan test result is measured on realistic messages only.

Run from the repo root:  python -I ml/src/features/dataset/kenya_sms.py
"""
import csv
import hashlib
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "ml"))

from src.features.dataset.public import clean, group_keys  # noqa: E402
from src.features.dataset.synthetic import COLUMNS, write_csv  # noqa: E402
from src.features.normalize.normalize import normalize  # noqa: E402
from src.features.scoring.categories import categorise  # noqa: E402

RAW = ROOT / "ml" / "data" / "raw" / "kenya_sms_mwatsefu.csv"
LABELS = ROOT / "ml" / "data" / "labels" / "kenya_sms_labels.csv"
OUT = ROOT / "ml" / "data" / "processed" / "kenya_sms.csv"
SOURCE = "kenya_sms"
VALID_LABELS = {"scam", "legit", "betting_ad", "unsure"}

# A few common Swahili words: enough to tag a message as Swahili or mixed for the fairness check.
_SW_WORDS = {"na", "ya", "kwa", "wa", "hii", "yako", "tuma", "pesa", "leo", "sasa", "kama", "niko",
             "tafadhali", "habari", "mungu", "umeshinda", "nitumie", "saa", "nimekosea", "asante"}


def raw_id(message: str) -> str:
    """The key the labels file uses: a fingerprint of the raw message, so the file never needs the text."""
    return hashlib.sha256(message.strip().encode("utf-8")).hexdigest()[:16]


def load_labels(path: Path = LABELS) -> dict[str, tuple[str, bool]]:
    with open(path, newline="", encoding="utf-8") as f:
        return {r["raw_id"]: (r["label"], r["looks_generated"] == "true") for r in csv.DictReader(f)}


def split_for(key: str, looks_generated: bool) -> str:
    """Stable split by group. Realistic messages: 60 / 10 / 30 (a bigger test, since Kenyan test data is the scarce
    thing). Generated-looking messages: 90 / 10, never test."""
    bucket = int(hashlib.sha256(key.encode("utf-8")).hexdigest(), 16) % 10
    if looks_generated:
        return "val" if bucket == 0 else "train"
    return "test" if bucket < 3 else "val" if bucket == 3 else "train"


def language(text: str) -> str:
    words = set(normalize(text).split())
    sw = len(words & _SW_WORDS)
    return "sw" if sw >= 2 else "mixed" if sw == 1 else "en"


def build(raw: Path = RAW, labels_path: Path = LABELS) -> tuple[list[dict], dict]:
    """Returns (rows, stats). A missing raw file gives no rows, so the pipeline still runs without it."""
    stats = {"read": 0, "unlabelled": 0, "unsure": 0, "duplicates": 0, "kept": 0}
    if not raw.exists():
        return [], stats
    labels = load_labels(labels_path)
    with open(raw, newline="", encoding="utf-8") as f:
        found = list(csv.DictReader(f))
    stats["read"] = len(found)
    rows, seen = [], set()
    for r in found:
        message = r["message_content"]
        entry = labels.get(raw_id(message))
        if entry is None:
            stats["unlabelled"] += 1
            continue
        label, generated = entry
        if label == "unsure":
            stats["unsure"] += 1
            continue
        # The file stores line breaks as a literal backslash-n; turn them back into spaces before cleaning.
        text = clean(message.replace("\\r\\n", " ").replace("\\n", " ").replace("\\t", " "))
        key = normalize(text)
        if key in seen:
            stats["duplicates"] += 1
            continue
        seen.add(key)
        is_scam = label == "scam"
        rows.append({
            "id": f"{SOURCE}-{len(rows):04d}", "text": text,
            "sender": "",  # the file has no sender: judged on its words alone, like pasted text
            "label": "scam" if is_scam else "legit",
            "campaign": categorise(text, None, set()) if is_scam else ("betting_ad" if label == "betting_ad" else "legit"),
            "source": SOURCE, "is_hard_negative": "true" if label == "betting_ad" else "false",
            "language": language(text), "split": "", "_generated": generated,
        })
    keys = group_keys([r["text"] for r in rows]) if rows else []
    # A group with any generated-looking member is kept out of the test whole, so no near-copy of a training
    # message can reach the test through its sibling.
    generated_groups = {k for r, k in zip(rows, keys) if r["_generated"]}
    for row, key in zip(rows, keys):
        row["split"] = split_for(key, key in generated_groups)
    stats["kept"] = len(rows)
    return [{c: r[c] for c in COLUMNS} for r in rows], stats


def main() -> None:
    rows, stats = build()
    if not rows:
        print(f"no Kenyan SMS file at {RAW}; see ml/data/README.md")
        return
    write_csv(OUT, rows)
    counts: dict[tuple, int] = {}
    for r in rows:
        k = (r["split"], r["label"], r["campaign"] if r["campaign"] == "betting_ad" else "")
        counts[k] = counts.get(k, 0) + 1
    print(f"wrote {len(rows)} rows to {OUT}  {stats}")
    for k in sorted(counts):
        print("  ", *[x for x in k if x], counts[k])


if __name__ == "__main__":
    main()
