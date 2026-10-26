"""GET /v1/numbers/{msisdn}/risk (5.2) and GET /v1/blocklist?since= (5.3)."""
import re
from datetime import datetime, timezone
from typing import Optional

from fastapi import APIRouter, HTTPException, Query, Request
from sqlalchemy import func, select

from src.core.db import IntelNumber, Report, Sender
from src.features.confirmation.service import status_of

router = APIRouter()
_MSISDN = re.compile(r"254[17]\d{8}")


def _iso(ts: Optional[float]) -> Optional[str]:
    return None if ts is None else datetime.fromtimestamp(ts, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


@router.get("/v1/numbers/{msisdn}/risk")
def number_risk(msisdn: str, request: Request) -> dict:
    if msisdn.upper() not in request.app.state.verified and not _MSISDN.fullmatch(msisdn):
        raise HTTPException(422, "msisdn must look like 2547XXXXXXXX")
    return risk_of(request.app, msisdn)


def risk_of(app, msisdn: str) -> dict:
    """What the gateway knows about one number (5.2). Also used by the USSD checker, which runs on the server."""
    if msisdn.upper() in app.state.verified:
        return {"msisdn": msisdn, "status": "allowlisted", "category": None, "reports": 0, "confirmed_at": None}
    caller = app.state.verified_callers.get(msisdn)
    if caller is not None:  # a genuine number: say who it is, never "risky"
        return {"msisdn": msisdn, "status": "verified", "category": None, "reports": 0, "confirmed_at": None,
                "name_en": caller.name_en, "name_sw": caller.name_sw}
    with app.state.sessions() as session:
        sender = session.get(Sender, msisdn)
        reports = session.scalar(select(func.count()).select_from(Report).where(Report.sender == msisdn))
        status = status_of(sender, app.state.clock())
        intel = session.get(IntelNumber, msisdn)
        if intel is not None:
            reports += intel.reports  # reports the fraud team received (333 and others)
            if status == "unknown" and intel.status == "reported":
                status = "suspected"  # reported to Safaricom but not proven: never on the phone blocklist
        category = sender.category if sender and status in ("confirmed", "suspected") else None
        if category is None and intel is not None and status in ("confirmed", "suspected"):
            category = intel.category
        return {
            "msisdn": msisdn,
            "status": status,
            "category": category,
            "reports": reports,
            "confirmed_at": _iso(sender.confirmed_at) if sender and status == "confirmed" else None,
        }


@router.get("/v1/blocklist")
def blocklist(request: Request, since: Optional[datetime] = Query(default=None)) -> dict:
    """Confirmed senders that changed since `since` (all of them when `since` is omitted)."""
    app = request.app
    now = app.state.clock()
    since_ts = 0.0
    if since is not None:
        since_ts = (since if since.tzinfo else since.replace(tzinfo=timezone.utc)).timestamp()
    with app.state.sessions() as session:
        changed = session.scalars(select(Sender).where(Sender.updated_at > since_ts)).all()
        added = [
            {"msisdn": s.msisdn, "category": s.category or "other"}
            for s in changed if s.confirmed_at is not None and not s.allowlisted
        ]
        removed = [s.msisdn for s in changed if s.confirmed_at is not None and s.allowlisted]
    return {"as_of": _iso(now), "added": added, "removed": removed}
