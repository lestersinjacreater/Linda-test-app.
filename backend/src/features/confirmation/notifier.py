"""Tells the telco/network that a sender is confirmed (contract 5.4).

POST {TELCO_URL}/network/confirm  {"sender": "..."}  -> {"warned_now": 214}
Retries 3 times with growing waits. A failure never un-confirms the sender: the radar's own
answers (risk lookup, blocklist) are still correct, only the network warning is delayed.
"""
import logging
import time
from typing import Callable

import httpx

from src.core.config import Settings

log = logging.getLogger("radar.notifier")

Notifier = Callable[[str], None]


def make_telco_notifier(settings: Settings, sleep: Callable[[float], None] = time.sleep) -> Notifier:
    def notify(sender: str) -> None:
        for attempt in range(1, settings.notify_retries + 1):
            try:
                r = httpx.post(f"{settings.telco_url}/network/confirm", json={"sender": sender}, timeout=5.0)
                r.raise_for_status()
                log.info("network told about %s: %s", sender, r.json())
                return
            except Exception as exc:  # network down, telco error, bad JSON: all handled the same way
                log.warning("telco notify attempt %d/%d for %s failed: %s", attempt, settings.notify_retries, sender, exc)
                if attempt < settings.notify_retries:
                    sleep(settings.notify_backoff_s * 2 ** (attempt - 1))
        log.error("giving up telling the network about %s", sender)

    return notify
