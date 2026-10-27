"""Real numbers for the dashboard (contract 5.14): how many Linda phones exist and what they caught.

Counts only. Simulated phones (device IDs starting with the demo prefix, see Settings.demo_trusted_prefix) and
the mock fraud team's seeded numbers are left out of everything here, so every number is a real phone's.
"""
import re

from sqlalchemy import func, select

from src.core.db import PhoneSeen, Report, Sender
from src.features.confirmation.service import status_of

DAY = 86400
RECENT = 8  # how many "latest scams caught" rows the dashboard shows
_DEVICE = re.compile(r"[0-9a-f]{8,64}")  # the same shape reports already require


def is_real_device(settings, device_id: str) -> bool:
    prefix = settings.demo_trusted_prefix
    return not (prefix and device_id.startswith(prefix))


def note_phone(app, device_id: str | None) -> None:
    """Remember that this phone checked in now. Junk or simulated IDs are ignored; failures never break the caller."""
    if not device_id:
        return
    device_id = device_id.lower()
    if not _DEVICE.fullmatch(device_id) or not is_real_device(app.state.settings, device_id):
        return
    now = app.state.clock()
    try:
        with app.state.sessions() as session:
            row = session.get(PhoneSeen, device_id)
            if row is None:
                session.add(PhoneSeen(device_id=device_id, first_seen=now, last_seen=now))
            else:
                row.last_seen = now
            session.commit()
    except Exception:  # a counter must never stop a phone getting its lookup or sync
        pass


def mask(msisdn: str) -> str:
    """254700000777 -> "0700 ••• 777". Enough to recognise, not enough to copy a flagged number off a public screen."""
    if len(msisdn) == 12 and msisdn.startswith("254"):
        local = "0" + msisdn[3:]
        return f"{local[:4]} ••• {local[-3:]}"
    return "•••"


def stats(app) -> dict:
    settings, now = app.state.settings, app.state.clock()
    prefix = settings.demo_trusted_prefix
    real_report = Report.device_id.not_like(f"{prefix}%") if prefix else Report.device_id.is_not(None)
    real_phone = PhoneSeen.device_id.not_like(f"{prefix}%") if prefix else PhoneSeen.device_id.is_not(None)
    with app.state.sessions() as session:
        phones_total = session.scalar(select(func.count()).select_from(PhoneSeen).where(real_phone))
        phones_active = session.scalar(
            select(func.count()).select_from(PhoneSeen).where(real_phone, PhoneSeen.last_seen > now - DAY))
        reports_total = session.scalar(select(func.count()).select_from(Report).where(real_report))
        reports_day = session.scalar(
            select(func.count()).select_from(Report).where(real_report, Report.received_at > now - DAY))
        by_category = dict(session.execute(
            select(Report.category, func.count()).where(real_report).group_by(Report.category)).all())
        # Only numbers that at least one real phone reported: the fraud team's seeded demo numbers do not count.
        real_senders = select(Report.sender).where(real_report).distinct()
        senders = session.scalars(select(Sender).where(Sender.msisdn.in_(real_senders))).all()
        status = {s.msisdn: status_of(s, now) for s in senders}
        latest = session.scalars(
            select(Report).where(real_report).order_by(Report.received_at.desc()).limit(RECENT)).all()
        recent = [{"category": r.category, "at": r.received_at, "number": mask(r.sender),
                   "status": status.get(r.sender, "unknown")} for r in latest]
    return {
        "as_of": now,
        "phones": {"total": phones_total, "active_24h": phones_active},
        "scams_caught": {"total": reports_total, "last_24h": reports_day},
        "numbers": {"confirmed": sum(v == "confirmed" for v in status.values()),
                    "suspected": sum(v == "suspected" for v in status.values())},
        "by_category": dict(sorted(by_category.items(), key=lambda kv: -kv[1])),
        "recent": recent,
    }
