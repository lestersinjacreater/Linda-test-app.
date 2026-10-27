"""Private lookups for phones (root CLAUDE.md 5.10) and the hashed offline sync (5.11).

GET /v1/lookup?prefix=a3f09  -> every known number whose hash starts with the prefix. The gateway never
                                learns which number the phone was asking about, and never sends a full list.
GET /v1/sync?since=<iso>     -> hashes of RECENT confirmed numbers only, for offline call screening.
"""
import re
from datetime import datetime, timezone
from typing import Optional

from fastapi import APIRouter, HTTPException, Query, Request
from sqlalchemy import func, select

from src.core.db import IntelNumber, Report, Sender
from src.features.confirmation.service import status_of
from src.features.private.hashing import PREFIX_LENGTH, number_hash
from src.features.private.labels import labels
from src.features.stats.service import note_phone

router = APIRouter()
_PREFIX = re.compile(rf"[0-9a-f]{{{PREFIX_LENGTH}}}")


def _iso(ts: float) -> str:
    return datetime.fromtimestamp(ts, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _check_rate(request: Request) -> None:
    device = request.headers.get("x-linda-device")
    key = device or (request.client.host if request.client else "unknown")
    if not request.app.state.lookup_limiter.allow(key):
        raise HTTPException(429, "too many lookups from this device; try again later")
    note_phone(request.app, device)  # "phones active" on the dashboard (5.14): the anonymous ID only


def _entry(msisdn: str, status: str, category: Optional[str], reports: int, name_en: str = "", name_sw: str = "") -> dict:
    label_en, label_sw = labels(status, reports, name_en, name_sw)
    return {"hash": number_hash(msisdn), "status": status, "category": category,
            "label": label_en, "label_sw": label_sw}


def known_numbers(app, session) -> dict[str, dict]:
    """Everything a phone may be told, by msisdn. Verified callers win over any flag.

    Hackathon scale: built per request. Production would keep a hash column with an index.
    """
    now = app.state.clock()
    out: dict[str, dict] = {}
    report_counts = dict(session.execute(select(Report.sender, func.count()).group_by(Report.sender)).all())
    intel = {i.msisdn: i for i in session.scalars(select(IntelNumber)).all()}
    for s in session.scalars(select(Sender)).all():
        status = status_of(s, now)
        if status in ("confirmed", "suspected"):
            extra = intel[s.msisdn].reports if s.msisdn in intel else 0
            out[s.msisdn] = _entry(s.msisdn, status, s.category, report_counts.get(s.msisdn, 0) + extra)
    for msisdn, i in intel.items():
        if msisdn not in out and i.status == "reported":
            out[msisdn] = _entry(msisdn, "reported", i.category, i.reports)
    for c in app.state.verified_callers.values():
        out[c.msisdn] = _entry(c.msisdn, "verified", None, 0, c.name_en, c.name_sw)
    return out


@router.get("/v1/lookup")
def lookup(request: Request, prefix: str = Query(...)) -> dict:
    _check_rate(request)
    prefix = prefix.lower()
    if not _PREFIX.fullmatch(prefix):
        raise HTTPException(422, f"prefix must be exactly {PREFIX_LENGTH} hex characters")
    with request.app.state.sessions() as session:
        entries = [e for e in known_numbers(request.app, session).values() if e["hash"].startswith(prefix)]
    return {"prefix": prefix, "entries": sorted(entries, key=lambda e: e["hash"])}


@router.get("/v1/sync")
def sync(request: Request, since: Optional[datetime] = Query(default=None)) -> dict:
    """Recent confirmed numbers changed since `since` (all recent ones when omitted), as hashes.

    `removed` = numbers cleared by a human (allowlisted) or that aged out of the recent window, so the
    phone's list stays short. Verified callers are public and come from GET /v1/verified-callers (5.13).
    """
    _check_rate(request)
    app = request.app
    now, window = app.state.clock(), app.state.settings.sync_recent_days * 86400
    since_ts = 0.0 if since is None else (since if since.tzinfo else since.replace(tzinfo=timezone.utc)).timestamp()
    never = app.state.verified | set(app.state.verified_callers)
    with app.state.sessions() as session:
        report_counts = dict(session.execute(select(Report.sender, func.count()).group_by(Report.sender)).all())
        intel = {i.msisdn: i.reports for i in session.scalars(select(IntelNumber)).all()}
        added, removed = [], []
        for s in session.scalars(select(Sender).where(Sender.confirmed_at.is_not(None))).all():
            if s.msisdn in never:
                continue
            aged_out_at = s.confirmed_at + window
            if s.allowlisted:
                if s.updated_at > since_ts:
                    removed.append(number_hash(s.msisdn))
            elif aged_out_at <= now:
                if aged_out_at > since_ts:
                    removed.append(number_hash(s.msisdn))
            elif s.updated_at > since_ts:
                added.append(_entry(s.msisdn, "confirmed", s.category or "other",
                                    report_counts.get(s.msisdn, 0) + intel.get(s.msisdn, 0)))
    return {"as_of": _iso(now), "added": added, "removed": removed}
