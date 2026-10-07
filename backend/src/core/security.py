"""Abuse protection: per-device rate limit and the poisoning alarm."""
import logging

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from src.core.config import Settings
from src.core.db import Device, Report, SecurityEvent

log = logging.getLogger("radar.security")


def is_rate_limited(session: Session, settings: Settings, device_id: str, now: float) -> bool:
    """True when this device already sent its hourly maximum."""
    count = session.scalar(
        select(func.count()).select_from(Report).where(
            Report.device_id == device_id, Report.received_at >= now - 3600
        )
    )
    return count >= settings.max_reports_per_device_per_hour


def check_poisoning_burst(session: Session, settings: Settings, sender: str, now: float) -> None:
    """Log (once per burst) when many first-time devices hit one sender within seconds.

    Real scam waves are reported by devices that have been around; a crowd of brand-new
    devices on one number is what a poisoning attempt looks like. We do not block on this
    (trust weights already stop it from confirming), we make it visible.
    """
    since = now - settings.poison_burst_window_s
    new_devices = session.scalar(
        select(func.count(func.distinct(Report.device_id)))
        .join(Device, Device.device_id == Report.device_id)
        .where(Report.sender == sender, Report.received_at >= since, Device.first_seen >= since)
    )
    if new_devices < settings.poison_burst_devices:
        return
    already = session.scalar(
        select(func.count()).select_from(SecurityEvent).where(
            SecurityEvent.kind == "poison_burst", SecurityEvent.sender == sender, SecurityEvent.at >= since
        )
    )
    if already:
        return
    detail = f"{new_devices} first-time devices reported this sender within {settings.poison_burst_window_s}s"
    session.add(SecurityEvent(kind="poison_burst", sender=sender, detail=detail, at=now))
    log.warning("possible poisoning of %s: %s", sender, detail)
