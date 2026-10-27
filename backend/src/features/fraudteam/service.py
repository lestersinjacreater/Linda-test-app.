"""The fraud-team console (contract 5.15): the screen a fraud analyst works from.

It shows what real Linda phones reported (simulated phones are left out, as on the public page), lets a person
confirm a number as a scam or clear it as not a scam, and lists what happened, newest first. Unlike the public page
it shows FULL numbers, because reviewing them is the fraud team's job; that is why it sits behind the presenter
password and an internal key (see router.py). Confirming here does exactly what three phones agreeing does: phones
get the number at their next sync, and the network warns everyone who heard from it.
"""
from collections import Counter, defaultdict

from sqlalchemy import func, select

from src.core.db import PhoneSeen, Report, Sender
from src.features.confirmation.service import allowlist_sender, status_of

NEW_S = 3600  # a number first reported within the last hour gets a "New" tag
FEED = 15


def pretty(msisdn: str) -> str:
    """254712345678 -> "0712 345 678"."""
    if len(msisdn) == 12 and msisdn.startswith("254"):
        local = "0" + msisdn[3:]
        return f"{local[:4]} {local[4:7]} {local[7:]}"
    return msisdn


def _real(settings, column):
    prefix = settings.demo_trusted_prefix
    return column.not_like(f"{prefix}%") if prefix else column.is_not(None)


def console(app) -> dict:
    settings, now = app.state.settings, app.state.clock()
    with app.state.sessions() as session:
        reports = session.scalars(
            select(Report).where(_real(settings, Report.device_id)).order_by(Report.received_at)).all()
        phones = session.scalar(
            select(func.count()).select_from(PhoneSeen).where(_real(settings, PhoneSeen.device_id)))
        by_sender: dict[str, list[Report]] = defaultdict(list)
        for r in reports:
            by_sender[r.sender].append(r)
        senders = {s.msisdn: s for s in session.scalars(select(Sender).where(Sender.msisdn.in_(list(by_sender)))).all()}

    queue, feed = [], []
    for msisdn, rs in by_sender.items():
        sender = senders.get(msisdn)
        status = status_of(sender, now)
        if status == "unknown":
            status = "reported"  # reported by real phones, not yet suspected or confirmed
        category = Counter(r.category for r in rs).most_common(1)[0][0]
        queue.append({
            "msisdn": msisdn, "number": pretty(msisdn), "category": category, "reports": len(rs),
            "phones": len({r.device_id for r in rs}), "first_at": rs[0].received_at, "last_at": rs[-1].received_at,
            "status": status, "new": now - rs[0].received_at < NEW_S,
        })
        if sender is not None and sender.confirmed_at is not None:
            feed.append({"at": sender.confirmed_at, "kind": "confirmed", "number": pretty(msisdn), "category": category})
    for r in reports[-FEED:]:
        feed.append({"at": r.received_at, "kind": "report", "number": pretty(r.sender), "category": r.category})

    waiting = [q for q in queue if q["status"] in ("reported", "suspected")]
    # Waiting numbers first (the work to do), then the rest; newest activity first inside each group.
    queue.sort(key=lambda q: (q["status"] not in ("reported", "suspected"), -q["last_at"]))
    return {
        "as_of": now,
        "counts": {"reports": len(reports), "waiting": len(waiting),
                   "confirmed": sum(q["status"] == "confirmed" for q in queue), "phones": phones},
        "queue": queue,
        "patterns": dict(Counter(r.category for r in reports).most_common()),
        "feed": sorted(feed, key=lambda f: -f["at"])[:FEED],
    }


class Refused(Exception):
    """A number the console must not confirm (a verified sender or caller), or one nobody reported."""


def confirm(app, msisdn: str) -> str:
    """A person on the fraud team confirms the number. Returns its category. Raises Refused for verified numbers."""
    never = app.state.verified | set(app.state.verified_callers)
    if msisdn in never or msisdn.upper() in never:
        raise Refused("a verified number is never confirmed as a scammer")
    now = app.state.clock()
    with app.state.sessions() as session:
        sender = session.get(Sender, msisdn)
        if sender is None:
            raise Refused("no phone has reported this number")
        if sender.confirmed_at is None or sender.allowlisted:
            categories = Counter(session.scalars(select(Report.category).where(Report.sender == msisdn)).all())
            sender.category = categories.most_common(1)[0][0] if categories else (sender.category or "other")
            sender.confirmed_at, sender.allowlisted = now, False
            sender.updated_at = now  # phones pick it up at their next sync (5.11)
            session.commit()
        return sender.category or "other"


def clear(app, msisdn: str) -> None:
    """A person on the fraud team says: not a scam. Phones drop it at their next sync."""
    with app.state.sessions() as session:
        allowlist_sender(session, msisdn, app.state.clock())
