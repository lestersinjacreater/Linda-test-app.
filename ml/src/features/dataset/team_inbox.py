"""Turns a team member's donated inbox into scrubbed training rows (ml/data/processed/team_collected.csv).

PRIVATE DATA. The raw file, the labels and the output are all git-ignored and stay on the machine that trains:
  - ml/data/raw/team_inbox.csv                    the donation export: donor_id, sender_id, sender_type, text, received_at
                                                  (personal senders already hashed as P_<hex> by the donation tool)
  - ml/data/labels/private/team_inbox_labels.csv  raw_id (16 hex characters of the SHA-256 of the raw text), label
  - ml/data/processed/team_collected.csv          scrubbed rows for the trainer
Only the trained model (hashed letter-piece weights) and aggregate metrics leave the machine.

Labels (reviewed by Claude, 25 Oct 2026; the donor keeps the file):
  - legit, scam, betting_ad (a gambling advert: trained as legit, reported separately, like kenya_sms.py)
  - unsure: left out. exclude: too sensitive to use even scrubbed (passwords, tax IDs, health, a minor's exam result).
  Messages from verified sender IDs (MPESA, Safaricom, ...) in a real inbox are labelled genuine by their sender.
Never used at all: Safaricom staff messages (StaffUpdate, SafaricomHR): employer-internal, not what customers receive.

Scrubbing (before anything else): names after "from/to/kutoka/Dear/Hi..." and the donor's family names become JOHN DOE,
phone and account numbers become 0700000000, M-Pesa transaction codes become a fake code of the same shape, e-mail
addresses and the donation tool's <OTP>/<PHONE_...> markers become neutral values.

Volume: thousands of near-identical M-Pesa and data-balance texts would drown out everything else, so each group of
near-identical messages from one sender keeps at most MAX_PER_GROUP rows. Groups never cross splits.

Run from the repo root:  python -I ml/src/features/dataset/team_inbox.py
"""
import csv
import hashlib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "ml"))

from src.features.dataset.kenya_sms import language  # noqa: E402
from src.features.dataset.public import clean, group_keys  # noqa: E402
from src.features.dataset.synthetic import COLUMNS, write_csv  # noqa: E402
from src.features.scoring.categories import categorise  # noqa: E402

RAW = ROOT / "ml" / "data" / "raw" / "team_inbox.csv"
LABELS = ROOT / "ml" / "data" / "labels" / "private" / "team_inbox_labels.csv"
OUT = ROOT / "ml" / "data" / "processed" / "team_collected.csv"
VERIFIED_PATH = ROOT / "shared" / "verified_senders.json"
SOURCE = "team_collected"
NEVER_USE = {"STAFFUPDATE", "SAFARICOMHR"}
MAX_PER_GROUP = 1
PERSONAL_SENDER = "254700000999"  # personal senders arrive hashed; the model only needs "this is a phone number"

# The donor's and close family's names (the donor asked for their inbox to be used; these must still never appear).
FAMILY = re.compile(r"\b(?:lester|sinja|watosia|wesley|wesly|judith|beryl|berylwatosia|kapchanga|lusike)\b", re.IGNORECASE)
_MARKER = re.compile(r"<(PHONE|CARD)_?[0-9a-f]*>")
_OTP = re.compile(r"<OTP>")
_EMAIL = re.compile(r"\b[\w.+-]+@[\w-]+\.[\w.]+\b")
_CODE = re.compile(r"(?<![A-Za-z0-9])(?=[A-Z0-9]{10}(?![A-Za-z0-9]))(?=[A-Z0-9]*[A-Z])(?=[A-Z0-9]*[0-9])[A-Z0-9]{10}")
# A name after a money word ("from JOHN KAMAU", "from Maria Muthoka", "to MERCY CHERONO", "kutoka kwa PETER OTIENO"):
# up to 4 capitalised words (Title case or CAPITALS), stopping at a brand or service word, which is kept.
_AFTER_MONEY_WORD = re.compile(r"\b(?:from|to|kutoka(?:\s+kwa)?|kwa|by|favor of|in favour of|sent by)\s+", re.IGNORECASE)
_NAME_WORD = re.compile(r"[A-Z][A-Za-z']*[A-Za-z]\b")
_BRANDS = {"MPESA", "M-PESA", "PESA", "SAFARICOM", "AIRTEL", "MONEY", "FULIZA", "SHWARI", "KCB", "EQUITY", "BANK", "HUSTLER",
           "FUND", "LIPA", "NA", "MY", "APP", "ONEAPP", "PAYBILL", "TILL", "ACCOUNT", "MOBILE", "LOOP", "NCBA", "DTB",
           "OKOA", "JAHAZI", "BONGA", "GLOBALPAY", "VISA", "ZIIDI", "KOPA", "DATA", "SMS", "THE", "YOUR", "OUR", "ALL",
           "GOOGLE", "NETFLIX", "SPOTIFY", "LINKEDIN", "MICROSOFT", "APPLE", "UBER", "KRA", "KPLC", "NSSF", "SHA"}


# A name before "has sent ..." ("Zachary Njoroge Kweri has sent you KES 1000", "ALEX KARIUKI has sent KShs. 1000").
_NAME_BEFORE_SENT = re.compile(r"\b[A-Z][A-Za-z']+(?:\s+[A-Z][A-Za-z']+){1,3}(?=\s+(?:has sent|sent you|amekutumia|ametuma))")


def _mask_names(t: str) -> str:
    out, pos = [], 0
    for m in _AFTER_MONEY_WORD.finditer(t):
        if m.start() < pos:
            continue
        i, words = m.end(), 0
        while words < 4:
            w = _NAME_WORD.match(t, i)
            if not w or w.group(0).upper() in _BRANDS:
                break
            i, words = w.end(), words + 1
            sp = re.match(r"\s+", t[i:])
            if sp and _NAME_WORD.match(t, i + sp.end()) and t[i + sp.end():i + sp.end() + 1].isupper():
                i += sp.end()
            else:
                break
        if words:
            end = i if t[i - 1:i] != " " else i - 1
            out.append(t[pos:m.end()] + "JOHN DOE")
            pos = end
    return "".join(out) + t[pos:]


# A name after a greeting ("Dear Ann", "Hi LESTER", "Hello madam Ann").
_GREETING_NAME = re.compile(r"\b((?:Dear|Hi|Hello|Hey|Habari|Jambo|Morning)\s+(?:madam\s+|sir\s+|mr\.?\s+|mrs\.?\s+)?)"
                            r"([A-Z][A-Za-z'-]+(?:\s+[A-Z][A-Za-z'-]+){0,2})\b")
_KEEP_AFTER_GREETING = {"Customer", "Client", "Subscriber", "Tenant", "Taxpayer", "Sir", "Madam", "All", "There",
                        "Team", "Friends", "Learner", "Kastomaa", "LOOPer", "iTax", "User", "Lord", "God"}


def raw_id(text: str) -> str:
    return hashlib.sha256(text.strip().encode("utf-8")).hexdigest()[:16]


def fake_code(code: str) -> str:
    """Same shape as an M-Pesa code (10 capitals/digits, at least one of each), derived from the real one."""
    h = hashlib.sha256(("linda-code:" + code).encode()).hexdigest().upper()
    letters = [c for c in h if c.isalpha()] + list("QKTXRB")
    digits = [c for c in h if c.isdigit()] + list("3579")
    return "".join(letters[:6] + digits[:1] + letters[6:8] + digits[1:2])


def scrub(text: str) -> str:
    t = text.replace("\r", " ").replace("\n", " ")
    t = _MARKER.sub("0700000000", t)
    t = _OTP.sub("482913", t)
    t = _EMAIL.sub("name@example.com", t)
    t = _CODE.sub(lambda m: fake_code(m.group(0)), t)
    t = _mask_names(t)
    t = _NAME_BEFORE_SENT.sub("JOHN DOE", t)
    t = _GREETING_NAME.sub(lambda m: m.group(0) if m.group(2).split()[0] in _KEEP_AFTER_GREETING else m.group(1) + "JOHN", t)
    t = FAMILY.sub("JOHN", t)
    return clean(t)  # phone and account numbers (9+ digits), "jina ..." names, spacing


def load_labels(path: Path = LABELS) -> dict[str, str]:
    with open(path, newline="", encoding="utf-8") as f:
        return {r["raw_id"]: r["label"] for r in csv.DictReader(f)}


def split_for(key: str) -> str:
    """Stable 60 / 10 / 30 by group: real Kenyan test data is the scarce thing, so the test is larger."""
    bucket = int(hashlib.sha256(key.encode("utf-8")).hexdigest(), 16) % 10
    return "test" if bucket < 3 else "val" if bucket == 3 else "train"


_MONEY_OR_SECURITY = re.compile(r"ksh|kes|confirmed|m-?pesa|pin|otp|loan|fuliza|paybill|till|bonus|win|reversal", re.I)


def build(raw: Path = RAW, labels_path: Path = LABELS) -> tuple[list[dict], dict]:
    """Returns (rows, stats). Without the private files there are no rows, and the pipeline still runs."""
    stats = {"read": 0, "staff": 0, "unlabelled": 0, "unsure": 0, "excluded": 0, "duplicates": 0, "capped": 0, "kept": 0}
    if not raw.exists() or not labels_path.exists():
        return [], stats
    import json
    verified = {s.upper() for s in json.loads(VERIFIED_PATH.read_text(encoding="utf-8"))["sender_ids"]}
    labels = load_labels(labels_path)
    with open(raw, newline="", encoding="utf-8-sig", errors="replace") as f:
        found = list(csv.DictReader(f))
    stats["read"] = len(found)
    candidates, seen = [], set()
    for r in found:
        sid = r["sender_id"].strip()
        if sid.upper().replace(" ", "") in NEVER_USE:
            stats["staff"] += 1
            continue
        label = labels.get(raw_id(r["text"]))
        if label is None:
            stats["unlabelled"] += 1
            continue
        if label in ("unsure", "exclude"):
            stats["unsure" if label == "unsure" else "excluded"] += 1
            continue
        text = scrub(r["text"])
        sender = PERSONAL_SENDER if r["sender_type"] == "personal" else sid
        if (sender, text) in seen:
            stats["duplicates"] += 1
            continue
        seen.add((sender, text))
        candidates.append({"raw": raw_id(r["text"]), "text": text, "sender": sender, "label": label})

    # Group near-identical messages per sender (personal senders together), then cap each group.
    by_sender: dict[str, list[dict]] = {}
    for c in candidates:
        by_sender.setdefault(c["sender"], []).append(c)
    rows = []
    for sender, items in sorted(by_sender.items()):
        keys = group_keys([c["text"] for c in items]) if len(items) > 1 else [items[0]["text"]]
        groups: dict[str, list[dict]] = {}
        for c, k in zip(items, keys):
            groups.setdefault(sender + "|" + k, []).append(c)
        for gkey, members in sorted(groups.items()):
            members.sort(key=lambda c: c["raw"])
            keep = members if any(m["label"] == "scam" for m in members) else members[:MAX_PER_GROUP]
            stats["capped"] += len(members) - len(keep)
            split = split_for(gkey)
            for c in keep:
                is_scam = c["label"] == "scam"
                rows.append({
                    "id": f"{SOURCE}-{len(rows):05d}", "text": c["text"], "sender": c["sender"],
                    "label": "scam" if is_scam else "legit",
                    "campaign": categorise(c["text"], c["sender"], verified) if is_scam
                    else ("betting_ad" if c["label"] == "betting_ad" else "legit"),
                    "source": SOURCE,
                    "is_hard_negative": "true" if (not is_scam and (c["label"] == "betting_ad" or _MONEY_OR_SECURITY.search(c["text"]))) else "false",
                    "language": language(c["text"]), "split": split,
                })
    stats["kept"] = len(rows)
    return [{k: r[k] for k in COLUMNS} for r in rows], stats


def main() -> None:
    rows, stats = build()
    if not rows:
        print(f"no private inbox files ({RAW.name} and {LABELS.name}); see ml/data/README.md")
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
