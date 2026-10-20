"""What the mock fraud team knows, kept in memory and reset from the seed file.

Statuses (root CLAUDE.md 5.12):
  reported  people reported it to 333; not proven
  blocked   the fraud team blocked it across the network
  campaign  the fraud team linked it to a known scam campaign
"""
import json
import time
from collections import deque
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Optional

STATUSES = ("reported", "blocked", "campaign")


def iso(ts: float) -> str:
    return datetime.fromtimestamp(ts, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


@dataclass
class Number:
    msisdn: str
    status: str
    category: str
    reports: int
    campaign_id: Optional[str]
    updated_at: float

    def to_dict(self) -> dict:
        return {"msisdn": self.msisdn, "status": self.status, "category": self.category,
                "reports": self.reports, "campaign_id": self.campaign_id}


@dataclass
class Store:
    seed_path: Path
    verified_path: Path
    inbox_limit: int = 500
    clock: Callable[[], float] = time.time
    numbers: dict[str, Number] = field(default_factory=dict)
    campaigns: list[dict] = field(default_factory=list)
    verified: list[dict] = field(default_factory=list)
    inbox: deque = field(default_factory=deque)

    def reset(self) -> None:
        seed = json.loads(self.seed_path.read_text(encoding="utf-8"))
        now = self.clock()
        self.numbers = {n["msisdn"]: Number(n["msisdn"], n["status"], n["category"], n["reports"],
                                            n.get("campaign_id"), now) for n in seed["numbers"]}
        self.campaigns = seed["campaigns"]
        callers = json.loads(self.verified_path.read_text(encoding="utf-8"))["callers"]
        self.verified = [{k: c[k] for k in ("msisdn", "name_en", "name_sw", "kind")} for c in callers]
        self.inbox = deque(maxlen=self.inbox_limit)

    def changed_since(self, since: Optional[float]) -> list[Number]:
        return [n for n in self.numbers.values() if since is None or n.updated_at > since]

    def flag(self, msisdn: str, status: str, category: str, campaign_id: Optional[str] = None) -> Number:
        """Presenter action: the fraud team acts on a number right now."""
        old = self.numbers.get(msisdn)
        n = Number(msisdn, status, category, (old.reports if old else 0), campaign_id, self.clock())
        self.numbers[msisdn] = n
        return n

    def receive(self, item: dict) -> None:
        """A report or confirmation forwarded by the Linda gateway. Report counts go up."""
        item = {**item, "received_at": iso(self.clock())}
        self.inbox.appendleft(item)
        if item["kind"] == "report" and item["sender"] in self.numbers:
            n = self.numbers[item["sender"]]
            n.reports += 1
            n.updated_at = self.clock()
