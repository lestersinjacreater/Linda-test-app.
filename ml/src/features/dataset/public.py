"""Turns the downloaded public datasets into rows the trainer understands.

Sources (download them yourself, put them in ml/data/raw/, never commit them):
  - bongoscam.csv          Kaggle "Swahili SMS Detection Dataset" (henrydioniz). Columns: Category, Sms
  - mendeley_smishing.csv  Mendeley "SMS Phishing Dataset" (Mishra & Soni), file Dataset_5971.csv.
                           Columns: LABEL, TEXT, URL, EMAIL, PHONE

Why we clean before training:
  - Bongoscam is Tanzanian. If we left "Tsh", "Halotel" or 06xx numbers in, the model would learn
    "Tanzanian words mean scam", which is useless in Kenya. So currency becomes Ksh, Tanzanian
    network names are removed, and every phone number becomes one placeholder.
  - Names of real people in the messages ("jina lije PETER NYANGE") are replaced by a placeholder.
  - Mendeley "spam" means adverts, not fraud. Linda only warns about fraud, so spam rows are dropped
    rather than guessed as scam or legit.

Splits: near-identical messages (variants of one scam script, character similarity >= 0.6) are
grouped together, and each group goes wholly to train, val or test. Without this, a third of the
test scams had a near-copy in training and the scores looked better than they really are. This is REAL data, so it may go in
`test`; it is reported separately from Kenyan data, which does not exist yet.

Run from the repo root:  python -I ml/src/features/dataset/public.py
"""
import csv
import hashlib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "ml"))

from src.features.dataset.synthetic import COLUMNS, write_csv  # noqa: E402
from src.features.normalize.normalize import normalize  # noqa: E402

RAW = ROOT / "ml" / "data" / "raw"
OUT = ROOT / "ml" / "data" / "processed" / "public.csv"

PHONE_PLACEHOLDER = "0700000000"
NAME_PLACEHOLDER = "JOHN DOE"

# Phone numbers: +255/+254/0 prefixed mobiles, UK premium numbers, any run of 9+ digits
# (spaces or dashes allowed inside).
_PHONE = re.compile(r"(?<!\d)(?:\+?\d{1,3}[\s-]?)?\d(?:[\s-]?\d){8,12}(?!\d)")
# "jina lije PETER NYANGE", "jina (PETER NYANGE)", "jina italeta Magomba Maila"
_NAME = re.compile(
    r"(\b(?i:jina)\s*(?i:lije|litakuja|litatokea|italeta|ni|la)?\s*[:(]?\s*)"
    r"((?!Ksh\b)[A-Z][A-Za-z']+(?:\s+(?!Ksh\b)[A-Z][A-Za-z']+){0,2})"  # capitalised words only
)
# "By JUMA SIKAREKO": a signature in capitals.
_SIGNED_NAME = re.compile(r"(\b[Bb][Yy]\s+)([A-Z]{2,}(?:\s+[A-Z]{2,}){0,2})\b")
_TZ_CURRENCY = re.compile(r"\b(?:tshs?|tzs)\.?(?=\s|\d|$)", re.IGNORECASE)
_TZ_NETWORKS = re.compile(r"\b(?:tigo|halotel|vodacom|ttcl|zantel|yas|mixx)\b", re.IGNORECASE)
_SPACES = re.compile(r"\s+")


def clean(text: str) -> str:
    """Remove personal and Tanzania-specific details. Order matters: currency, then names, then numbers."""
    t = str(text).replace("�", " ")
    t = _TZ_CURRENCY.sub("Ksh", t)  # before names, so "Tsh" is never mistaken for a name
    t = _NAME.sub(lambda m: m.group(1) + NAME_PLACEHOLDER, t)
    t = _SIGNED_NAME.sub(lambda m: m.group(1) + NAME_PLACEHOLDER, t)
    t = _PHONE.sub(PHONE_PLACEHOLDER, t)
    t = _TZ_NETWORKS.sub(" ", t)
    return _SPACES.sub(" ", t).strip()


SIMILAR = 0.6  # character-trigram cosine similarity at which two messages count as one script


def group_keys(texts: list[str]) -> list[str]:
    """Give near-identical messages the same key (connected groups of similar pairs)."""
    from sklearn.feature_extraction.text import TfidfVectorizer
    norm = [re.sub(r"[0-9]+", " ", normalize(t)) for t in texts]
    X = TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5)).fit_transform(norm)
    sim = (X @ X.T).tocoo()
    parent = list(range(len(texts)))

    def find(i: int) -> int:
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    for i, j, v in zip(sim.row, sim.col, sim.data):
        if i < j and v >= SIMILAR:
            parent[find(i)] = find(j)
    members: dict[int, list[str]] = {}
    for i in range(len(texts)):
        members.setdefault(find(i), []).append(norm[i])
    # Key = the group's smallest normalised text, so it does not depend on row order.
    return [min(members[find(i)]) for i in range(len(texts))]


def split_for(key: str) -> str:
    """Stable 70 / 10 / 20 split by group, so a script never sits in two splits."""
    bucket = int(hashlib.sha256(key.encode("utf-8")).hexdigest(), 16) % 10
    return "test" if bucket < 2 else "val" if bucket == 2 else "train"


def _read(path: Path) -> list[dict]:
    with open(path, newline="", encoding="utf-8", errors="replace") as f:
        return list(csv.DictReader(f))


def bongoscam_rows(path: Path) -> list[dict]:
    labels = {"scam": "scam", "trust": "legit"}
    out = []
    for r in _read(path):
        label = labels.get(r["Category"].strip().lower())
        if label:
            out.append((r["Sms"], label, "bongoscam", "sw"))
    return out


def mendeley_rows(path: Path) -> list[dict]:
    labels = {"smishing": "scam", "ham": "legit"}  # "spam" (adverts) is dropped on purpose
    out = []
    for r in _read(path):
        label = labels.get(r["LABEL"].strip().lower())
        if label:
            out.append((r["TEXT"], label, "mendeley_smishing", "en"))
    return out


def build(raw: Path = RAW) -> tuple[list[dict], dict]:
    """Returns (rows, stats). Missing files are skipped, so the pipeline still runs without them."""
    found = []
    for name, reader in (("bongoscam.csv", bongoscam_rows), ("mendeley_smishing.csv", mendeley_rows)):
        if (raw / name).exists():
            found += reader(raw / name)
    rows, seen, stats = [], {}, {"read": len(found), "too_short": 0, "duplicates": 0, "conflicts": 0}
    for text, label, source, lang in found:
        t = clean(text)
        key = normalize(t)
        if len(key) < 8:
            stats["too_short"] += 1
            continue
        if key in seen:
            if seen[key]["label"] != label:
                seen[key]["conflict"] = True  # same words, different labels: trust neither
            stats["duplicates"] += 1
            continue
        row = {
            "id": f"{source}-{len(rows):05d}", "text": t,
            "sender": "",  # unknown: the scorer judges it on its words alone, like pasted text
            "label": label, "campaign": source if label == "scam" else "legit",
            "source": source, "is_hard_negative": "false", "language": lang,
            "split": "",  # set below, once near-duplicates are grouped
        }
        seen[key] = row
        rows.append(row)
    kept = [r for r in rows if not seen[normalize(r["text"])].get("conflict")]
    stats["conflicts"] = len(rows) - len(kept)
    for r in kept:
        seen[normalize(r["text"])].pop("conflict", None)
    stats["kept"] = len(kept)
    for r, key in zip(kept, group_keys([r["text"] for r in kept]) if kept else []):
        r["split"] = split_for(key)
    return [{c: r[c] for c in COLUMNS} for r in kept], stats


def main() -> None:
    rows, stats = build()
    if not rows:
        print(f"no public datasets found in {RAW}; see ml/data/README.md")
        return
    write_csv(OUT, rows)
    counts = {}
    for r in rows:
        k = (r["source"], r["label"], r["split"])
        counts[k] = counts.get(k, 0) + 1
    print(f"wrote {len(rows)} rows to {OUT}  {stats}")
    for k in sorted(counts):
        print("  ", *k, counts[k])


if __name__ == "__main__":
    main()
