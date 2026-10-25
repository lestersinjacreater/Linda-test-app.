"""Inbox replay: how Linda would behave on a real phone, message by message, in date order.

It feeds a whole inbox to a Python mirror of the app's decision (the model, the business-sender rule, and the "inbox
story" layer from android/.../features/learning/InboxStory.kt) in the order the messages arrived. The story layer only
knows what came BEFORE each message, exactly as on a phone, so this is an honest test of it even on an inbox the story
rules were designed with. Use a model that never trained on the inbox (pass --model) for honest model numbers too.

Private data: it reads the git-ignored donated inbox and labels, and prints ONLY totals and sender names, never text.
Run from the repo root:
  python -I ml/src/features/evaluate/inbox_replay.py --model path/to/model.json
"""
import argparse
import collections
import csv
import hashlib
import re
import sys
from datetime import datetime, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "ml"))

from src.features.fingerprint.simhash import simhash64  # noqa: E402
from src.features.normalize.normalize import normalize  # noqa: E402
from src.features.scoring.metadata import metadata_features  # noqa: E402
from src.features.scoring.scorer import Scorer  # noqa: E402

RAW = ROOT / "ml" / "data" / "raw" / "team_inbox.csv"
LABELS = ROOT / "ml" / "data" / "labels" / "private" / "team_inbox_labels.csv"
NEVER_USE = {"STAFFUPDATE", "SAFARICOMHR"}

# ---- mirror of features/detection/Senders.kt ----
BRANDS = ["MPESA", "SAFARICOM", "FULIZA", "MSHWARI", "EQUITY", "KCB", "NCBA", "COOP", "KPLC", "KRA"]
PIN = re.compile(r"(?<![a-z0-9])(?:pin|nambari yako ya siri)(?![a-z0-9])")
BRAND_IN_TEXT = re.compile(r"(?<![a-z0-9])(?:mpesa|safaricom|fuliza)(?![a-z0-9])")
PERSONAL_IN_TEXT = re.compile(r"(?:\+?254|(?<![0-9])0)[17][0-9]{8}(?![0-9])")
LINK = re.compile(r"https?://|www\.|\b(?:bit\.ly|tinyurl\.com|t\.co|cutt\.ly|goo\.gl)/", re.I)

# ---- mirror of features/learning/InboxStory.kt (keep the numbers in step) ----
ROUTINE_MIN_MESSAGES = 5
ROUTINE_MIN_DAYS = 14
LOOKALIKE_MIN_MESSAGES = 10
LOOKALIKE_MIN_DAYS = 30
TEMPLATE_DISTANCE = 12        # SimHash bits that may differ for "the same kind of message"
MAX_TEMPLATES = 30
CODE = re.compile(r"(?<![A-Za-z0-9])(?=[A-Z0-9]{10}(?![A-Za-z0-9]))(?=[A-Z0-9]*[A-Z])(?=[A-Z0-9]*[0-9])[A-Z0-9]{10}")
# A web address: dotted words ending in a web ending. Not any "word.word": texts like "Confirmed.You" or
# "Ksh5.00.Amount" glue sentences together without a space, and those are not links.
HOST = re.compile(
    r"(?<![a-z0-9@.-])(?:https?://)?((?:[a-z0-9-]+\.)+(?:com|ke|net|org|info|io|ly|me|app|xyz|link|site|online|top|"
    r"africa|live|shop|club|vip|gl|co|cx|gle|to|us|as|at|in))(?![a-z0-9-])", re.I)
PAYMENT_CHANGED = re.compile(
    r"(?<![a-z])(?:new (?:paybill|till|account|buy goods|payment)|our new|has changed|have changed|changed our|"
    r"substituted|now via|now through|should be (?:submitted|paid|made) (?:through|to|via)|no other payment|"
    r"paybill mpya|till mpya|akaunti mpya|namba mpya ya)(?![a-z])")
PAYMENT_PLACE = re.compile(r"(?<![a-z])(?:paybill|till|buy goods|acc(?:ount)?\.? ?no|account number|a/c)(?![a-z])")


def kind_of(sender, sender_type, verified):
    if sender_type == "personal":
        return "PERSONAL"
    if sender.upper() in verified:
        return "VERIFIED"
    if re.fullmatch(r"[0-9]{3,6}", sender):
        return "SHORT_CODE"
    key = "".join(c for c in sender.upper() if c.isalnum())
    return "LOOKALIKE" if any(b in key for b in BRANDS) else "BUSINESS"


def strong_sign(text, meta):
    n = normalize(text)
    return bool(meta["has_link"] or meta["fake_mpesa"] or PIN.search(n) or BRAND_IN_TEXT.search(n)
                or PERSONAL_IN_TEXT.search(text.replace(" ", "")))


def hosts(text):
    return {h.lower().removeprefix("www.") for h in HOST.findall(text)}


def code_hash(code):
    return hashlib.sha256(code.upper().encode()).hexdigest()


class Story:
    """What a phone has seen so far: per sender, and the M-Pesa codes on it."""

    def __init__(self):
        self.senders = {}
        self.codes = set()

    def sender(self, s):
        return self.senders.get(s)

    def established(self, s, kind, at):
        h = self.senders.get(s)
        if not h:
            return False
        n, days = (LOOKALIKE_MIN_MESSAGES, LOOKALIKE_MIN_DAYS) if kind == "LOOKALIKE" else (ROUTINE_MIN_MESSAGES, ROUTINE_MIN_DAYS)
        return h["count"] >= n and at - h["first"] >= timedelta(days=days)

    def routine(self, s, text):
        h = self.senders.get(s)
        if not h:
            return False
        fp = int(simhash64(normalize(text)), 16)
        if any(bin(fp ^ t).count("1") <= TEMPLATE_DISTANCE for t in h["templates"]):
            return True
        hs = hosts(text)
        return bool(hs) and hs <= h["domains"]

    def remember(self, s, kind, text, at, reported):
        """Every text teaches what this sender usually sends, unless the person reported it (the replay has no
        reports). Learning only from texts Linda did not warn on would never learn a sender it wrongly warned on."""
        h = self.senders.setdefault(s, {"first": at, "count": 0, "templates": [], "domains": set()})
        h["count"] += 1
        if not reported and kind != "PERSONAL":
            h["templates"] = (h["templates"] + [int(simhash64(normalize(text)), 16)])[-MAX_TEMPLATES:]
            h["domains"] |= hosts(text)


def decide(scorer, story, text, sender, key, kind, at, start, use_story):
    """[sender] is what the model sees (a placeholder for personal numbers); [key] is the real sender for the story."""
    detail_meta = dict(zip(scorer.meta_order, metadata_features(text, sender, scorer.verified)))
    p = scorer.score(text, sender)
    level = "SCAM" if p >= scorer.scam else "CAUTION" if p >= scorer.warn else "SAFE"
    registered = kind in ("BUSINESS", "SHORT_CODE", "LOOKALIKE")
    strong = strong_sign(text, detail_meta)
    if registered and level != "SAFE" and not strong:
        level = "CAUTION" if kind == "LOOKALIKE" else "SAFE"
    why = None
    if not use_story or kind == "VERIFIED":
        return level, why
    codes = CODE.findall(text)
    matched = any(code_hash(c) in story.codes for c in codes)
    hard = bool(PIN.search(normalize(text)) or PERSONAL_IN_TEXT.search(text.replace(" ", "")) or detail_meta["fake_mpesa"])
    if registered and level != "SAFE":
        h = story.sender(key)
        new_domain = bool(hosts(text) - (h["domains"] if h else set()))
        if matched and not hard:
            level, why = "SAFE", "code_match"
        elif story.established(key, kind, at) and story.routine(key, text) and not hard and not new_domain:
            level, why = "SAFE", "routine_sender"
    # (Tried and dropped, 26 Oct: warning on a code that is NOT among the phone's M-Pesa messages. On the real inbox it
    # fired only on genuine bank texts, which often arrive seconds before the M-Pesa message, and never on a scam.)
    # "The way to pay has changed" from a phone number that is not an established contact.
    if kind == "PERSONAL" and not story.established(key, kind, at):
        n = normalize(text)
        if PAYMENT_CHANGED.search(n) and PAYMENT_PLACE.search(n):
            if level == "SAFE":
                level = "CAUTION"
            why = why or "payment_changed"
    return level, why


def replay(model_path, use_story):
    scorer = Scorer.from_file(model_path)
    labels = {r["raw_id"]: r["label"] for r in csv.DictReader(open(LABELS, encoding="utf-8"))}
    rows = list(csv.DictReader(open(RAW, newline="", encoding="utf-8-sig", errors="replace")))
    rows.sort(key=lambda r: r["received_at"])
    when = lambda r: datetime.fromisoformat(r["received_at"].replace("Z", "+00:00"))
    start = when(rows[0])
    story, cache = Story(), {}
    out = {"genuine": 0, "genuine_warned": 0, "scam": 0, "scam_warned": 0, "why": collections.Counter(),
           "warned_by_sender": collections.Counter()}
    for r in rows:
        sid = r["sender_id"].strip()
        if sid.upper().replace(" ", "") in NEVER_USE:
            continue
        label = labels.get(hashlib.sha256(r["text"].strip().encode("utf-8")).hexdigest()[:16])
        kind = kind_of(sid, r["sender_type"], scorer.verified)
        sender = "254700000999" if kind == "PERSONAL" else sid
        key = sid  # the story is kept per real sender (hashed personal ids stay distinct)
        at = when(r)
        level, why = decide(scorer, story, r["text"], sender, key, kind, at, start, use_story)
        if label in ("legit", "betting_ad"):
            out["genuine"] += 1
            if level != "SAFE":
                out["genuine_warned"] += 1
                out["warned_by_sender"]["(personal)" if kind == "PERSONAL" else sid] += 1
        elif label == "scam":
            out["scam"] += 1
            out["scam_warned"] += level != "SAFE"
        if why:
            out["why"][(why, "scam" if label == "scam" else "genuine")] += 1
        if sid.upper() in ("MPESA", "M-PESA"):
            story.codes |= {code_hash(c) for c in CODE.findall(r["text"])}
        story.remember(key, kind, r["text"], at, reported=False)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--model", default=str(ROOT / "android" / "app" / "src" / "main" / "assets" / "model.json"))
    args = ap.parse_args()
    if not RAW.exists() or not LABELS.exists():
        print("no private inbox files; nothing to replay")
        return
    for use_story in (False, True):
        o = replay(args.model, use_story)
        name = "with the inbox story" if use_story else "app today (no story)"
        print(f"{name:24}: genuine warned {o['genuine_warned']} of {o['genuine']} "
              f"({100 * o['genuine_warned'] / o['genuine']:.2f}%), scams warned {o['scam_warned']} of {o['scam']}")
        print(f"{'':24}  top senders still warned: {o['warned_by_sender'].most_common(8)}")
        if use_story:
            print(f"{'':24}  story decisions: {dict(o['why'])}")


if __name__ == "__main__":
    main()
