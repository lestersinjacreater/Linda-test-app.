"""Names the type of scam, for reports and for the reason shown to the user.

Simple keyword rules on the NORMALISED text, checked in this order (first match wins), so the
Kotlin app can copy them exactly. The model says WHETHER a message is a scam; this says WHICH kind.
"""
import re

from src.features.normalize.normalize import normalize
from src.features.scoring.metadata import FEATURE_NAMES, metadata_features

_RULES = [
    ("sent_by_mistake", ["kimakosa", "kwa makosa", "by mistake", "in error", "wrong number", "isiyo sahihi", "sent in error"]),
    ("prize", ["umeshinda", "mshindi", "you have won", "have won", "lucky draw", "mega draw", "won ksh"]),
    ("fuliza_upgrade", ["fuliza", "your mpesa limit", "boost your mpesa"]),
    ("kra_refund", ["kra"]),
    ("job_fee", ["job offer", "vacancy", "hiring", "interview fee", "kazi", "mshahara", "nafasi za kazi", "wafanyakazi"]),
    ("loan_fee", ["loan", "mkopo"]),
    ("pin_request", ["pin", "nambari yako ya siri"]),
]


def _has(text: str, phrase: str) -> bool:
    return re.search(r"(?<![a-z0-9])" + re.escape(phrase) + r"(?![a-z0-9])", text) is not None


def categorise(text: str, sender: str | None, verified: set[str]) -> str:
    meta = dict(zip(FEATURE_NAMES, metadata_features(text, sender, verified)))
    if meta["fake_mpesa"]:
        return "fake_mpesa"
    normalized = normalize(text)
    for category, phrases in _RULES:
        if any(_has(normalized, p) for p in phrases):
            return category
    if meta["has_link"]:
        return "phishing_link"
    return "other"
