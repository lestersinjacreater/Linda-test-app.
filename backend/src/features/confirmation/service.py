"""The heart of the radar: deciding when a sender is confirmed (backend/CLAUDE.md).

Plain-English version of the rule:
  1. Every phone gets ONE vote per sender inside the time window, however many times it reports.
  2. A vote is worth  trust x confidence.  New phones start at low trust (0.3).
  3. All the "unproven" phones together can add at most `unproven_score_cap` (1.2), so a crowd
     of brand-new fake phones can never reach the confirmation score on their own.
  4. A sender is CONFIRMED when at least 3 phones voted AND the total reaches 2.4.
  5. Trust grows when a phone's report ends up confirmed and shrinks when it never does.
"""
import json
from collections import Counter
from dataclasses import dataclass

from sqlalchemy import select
from sqlalchemy.orm import Session

from src.core.config import Settings
from src.core.db import Device, Report, Sender
from src.core.security import check_poisoning_burst, is_rate_limited


class RateLimited(Exception):
    pass


@dataclass
class Votes:
    devices: int
    score: float
    category: str


def load_verified_senders(settings: Settings) -> set[str]:
    data = json.loads(settings.verified_senders_path.read_text(encoding="utf-8"))
    return {s.upper() for s in data["sender_ids"]}


def effective_trust(device: Device, settings: Settings) -> float:
    """A device that never sent an integrity token is held to `no_token_trust_cap` (default 1.0: it can earn full trust)."""
    if device.has_integrity_token:
        return device.trust
    return min(device.trust, settings.no_token_trust_cap)


def current_votes(session: Session, settings: Settings, sender: str, now: float) -> Votes:
    """Count one vote per device (its most confident report) inside the window."""
    rows = session.execute(
        select(Report, Device)
        .join(Device, Device.device_id == Report.device_id)
        .where(Report.sender == sender, Report.received_at >= now - settings.window_s)
    ).all()
    best: dict[str, tuple[Report, Device]] = {}
    for report, device in rows:
        if device.device_id not in best or report.confidence > best[device.device_id][0].confidence:
            best[device.device_id] = (report, device)

    proven = unproven = 0.0
    for report, device in best.values():
        trust = effective_trust(device, settings)
        weight = trust * report.confidence
        if trust <= settings.new_device_trust + 1e-9:
            unproven += weight
        else:
            proven += weight
    score = proven + min(unproven, settings.unproven_score_cap)
    categories = Counter(r.category for r, _ in best.values())
    category = categories.most_common(1)[0][0] if categories else "other"
    return Votes(devices=len(best), score=score, category=category)


def settle_old_reports(session: Session, settings: Settings, now: float) -> None:
    """Lower trust for reports whose window passed without the sender being confirmed."""
    old = session.scalars(
        select(Report).where(Report.settled.is_(False), Report.received_at < now - settings.window_s)
    ).all()
    for report in old:
        sender = session.get(Sender, report.sender)
        if sender is not None and sender.confirmed_at is None:
            device = session.get(Device, report.device_id)
            device.trust = max(0.0, device.trust - settings.trust_loss)
        report.settled = True


def _reward_voters(session: Session, settings: Settings, sender: str, now: float) -> None:
    reports = session.scalars(
        select(Report).where(Report.sender == sender, Report.received_at >= now - settings.window_s)
    ).all()
    for device_id in {r.device_id for r in reports}:
        device = session.get(Device, device_id)
        device.trust = min(1.0, device.trust + settings.trust_gain)
    for r in reports:
        r.settled = True


def status_of(sender: Sender | None, now: float) -> str:
    if sender is None:
        return "unknown"
    if sender.allowlisted:
        return "allowlisted"
    if sender.confirmed_at is not None:
        return "confirmed"
    if sender.suspected_until > now:
        return "suspected"
    return "unknown"


def _matches_confirmed_campaign(session: Session, settings: Settings, fingerprint: str) -> bool:
    confirmed = session.scalars(
        select(Sender.fingerprint).where(Sender.confirmed_at.is_not(None), Sender.fingerprint.is_not(None))
    ).all()
    mine = int(fingerprint, 16)
    return any(bin(mine ^ int(fp, 16)).count("1") <= settings.fingerprint_distance for fp in confirmed)


@dataclass
class ReportResult:
    status: str
    devices: int
    newly_confirmed: bool = False


def submit_report(session: Session, settings: Settings, verified: set[str], report, now: float) -> ReportResult:
    """Store one report and re-evaluate the sender. `report` is the validated ReportIn."""
    if report.sender.upper() in verified:  # real M-Pesa, bank, KPLC senders: never stored
        return ReportResult("allowlisted", 0)

    settle_old_reports(session, settings, now)

    if is_rate_limited(session, settings, report.device_id, now):
        raise RateLimited()

    device = session.get(Device, report.device_id)
    if device is None:
        starting_trust = settings.new_device_trust
        if settings.demo_trusted_prefix and report.device_id.startswith(settings.demo_trusted_prefix):
            starting_trust = settings.demo_trusted_trust  # demo only, see Settings
        device = Device(device_id=report.device_id, trust=starting_trust, first_seen=now)
        session.add(device)
    if report.integrity_token:
        device.has_integrity_token = True  # hackathon: not verified; production must check Play Integrity

    sender = session.get(Sender, report.sender)
    is_new_sender = sender is None
    if sender is None:
        sender = Sender(msisdn=report.sender, fingerprint=report.fingerprint)
        session.add(sender)
    if sender.allowlisted:
        return ReportResult("allowlisted", 0)

    session.add(Report(
        sender=report.sender, device_id=report.device_id, category=report.category,
        confidence=report.confidence, fingerprint=report.fingerprint, model_version=report.model_version,
        sent_at=report.sent_at.isoformat(), received_at=now,
    ))
    session.flush()

    check_poisoning_burst(session, settings, report.sender, now)
    votes = current_votes(session, settings, report.sender, now)

    newly_confirmed = False
    if sender.confirmed_at is None:
        if votes.devices >= settings.min_devices and votes.score >= settings.min_score:
            sender.confirmed_at = now
            sender.category = votes.category
            newly_confirmed = True
            _reward_voters(session, settings, report.sender, now)
        else:
            sender.category = votes.category
            # "Suspected" = two phones agree, OR a new number is sending a known confirmed
            # campaign (fingerprint match). Never confirmed on a fingerprint alone.
            if votes.devices >= settings.suspect_devices or (
                is_new_sender and _matches_confirmed_campaign(session, settings, report.fingerprint)
            ):
                sender.suspected_until = now + settings.window_s
    sender.updated_at = now
    session.commit()
    return ReportResult(status_of(sender, now), votes.devices, newly_confirmed)


def allowlist_sender(session: Session, msisdn: str, now: float) -> None:
    """Human override: this number is fine. It stops being confirmed and shows up in `removed`."""
    sender = session.get(Sender, msisdn) or Sender(msisdn=msisdn)
    sender.allowlisted = True
    sender.updated_at = now
    session.add(sender)
    session.commit()
