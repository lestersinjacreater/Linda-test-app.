"""Verified callers: genuine numbers (Safaricom Customer Care, later banks and businesses).

Why: scammers pretend to be Safaricom. If a genuine call shows a verified badge, a call claiming
to be Safaricom WITHOUT the badge stands out. The list is public information, so the whole list
may be sent to phones (unlike flagged numbers, root CLAUDE.md principle 6).
"""
import json
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class VerifiedCaller:
    msisdn: str
    name_en: str
    name_sw: str
    kind: str


def load_verified_callers(path: Path) -> tuple[str, dict[str, VerifiedCaller]]:
    """Returns (list version, callers by msisdn)."""
    data = json.loads(path.read_text(encoding="utf-8"))
    callers = {
        c["msisdn"]: VerifiedCaller(c["msisdn"], c["name_en"], c["name_sw"], c["kind"])
        for c in data["callers"]
    }
    return data["version"], callers
