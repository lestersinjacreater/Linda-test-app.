"""Brings the fraud team's knowledge into the gateway (root CLAUDE.md 5.12).

Merge rules, in plain words:
  - "blocked" or "campaign" from the fraud team counts as CONFIRMED: the number reaches phones through
    the blocklist, and the network is told so it can warn everyone who heard from it.
  - "reported" (people told 333, not proven) counts as SUSPECTED in risk lookups only. It never reaches
    the phone blocklist on its own: one report is not proof.
  - A verified number (Safaricom Customer Care...) is never confirmed, whatever the source says.
  - Campaign fingerprints let a brand-new number sending a known script be marked suspected at once.
  - The fraud team's verified list is added to the gateway's list of verified callers.
If the fraud service is down, nothing changes: the gateway keeps what it already has.
"""
import logging
from datetime import datetime, timezone

from sqlalchemy import delete

from src.core.db import IntelFingerprint, IntelNumber, Sender
from src.features.verified.service import VerifiedCaller

log = logging.getLogger("gateway.intel")
CONFIRMING = ("blocked", "campaign")


def _iso(ts: float) -> str:
    return datetime.fromtimestamp(ts, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def sync_intel(app) -> dict:
    """One sync pass. Returns a small summary (also handy in tests and logs)."""
    client, now = app.state.intel_client, app.state.clock()
    if client is None:
        return {"enabled": False}
    try:
        numbers = client.numbers(app.state.intel_cursor)
        verified = client.verified()
        campaigns = client.campaigns()
    except Exception as exc:
        log.warning("fraud intelligence unreachable, keeping what we have: %s", exc)
        return {"enabled": True, "ok": False}

    for v in verified.get("numbers", []):
        app.state.verified_callers.setdefault(
            v["msisdn"], VerifiedCaller(v["msisdn"], v["name_en"], v["name_sw"], v.get("kind", "business")))
    never_confirm = app.state.verified | set(app.state.verified_callers)

    newly_confirmed = []
    with app.state.sessions() as session:
        for n in numbers.get("numbers", []):
            msisdn = n["msisdn"]
            if msisdn in never_confirm:
                continue
            row = session.get(IntelNumber, msisdn) or IntelNumber(msisdn=msisdn)
            row.status, row.category = n["status"], n.get("category") or "other"
            row.reports, row.campaign_id, row.synced_at = int(n.get("reports", 0)), n.get("campaign_id"), now
            session.add(row)
            if row.status in CONFIRMING:
                sender = session.get(Sender, msisdn)
                if sender is None:
                    sender = Sender(msisdn=msisdn)
                    session.add(sender)
                if sender.confirmed_at is None and not sender.allowlisted:
                    sender.confirmed_at, sender.category = now, row.category
                    sender.updated_at = now  # so phones pick it up in their next blocklist sync
                    newly_confirmed.append(msisdn)
        session.execute(delete(IntelFingerprint))
        for c in campaigns.get("campaigns", []):
            for fp in c.get("fingerprints", []):
                session.add(IntelFingerprint(campaign_id=c["id"], category=c.get("category", "other"), fingerprint=fp))
        session.commit()

    app.state.intel_cursor = numbers.get("as_of", app.state.intel_cursor)
    for msisdn in newly_confirmed:
        app.state.notifier(msisdn)  # the network warns everyone who heard from it
    return {"enabled": True, "ok": True, "numbers": len(numbers.get("numbers", [])),
            "newly_confirmed": newly_confirmed, "synced_at": _iso(now)}


def forward_item(app, kind: str, report, at: float) -> None:
    """Send a report or a confirmation back to the fraud team. Never any message text."""
    client = app.state.intel_client
    if client is None:
        return
    item = {"kind": kind, "sender": report.sender, "category": report.category, "at": _iso(at)}
    if kind == "report":
        item.update(confidence=report.confidence, fingerprint=report.fingerprint,
                    model_version=report.model_version, device_id=report.device_id)
    client.forward(item)
