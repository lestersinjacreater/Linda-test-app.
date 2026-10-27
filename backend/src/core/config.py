"""All tunable numbers in one place, read from environment variables (never hardcoded secrets)."""
import os
from dataclasses import dataclass
from pathlib import Path

_DEFAULT_VERIFIED = Path(__file__).resolve().parents[3] / "shared" / "verified_senders.json"
_DEFAULT_CALLERS = Path(__file__).resolve().parents[3] / "shared" / "verified_callers.json"


@dataclass(frozen=True)
class Settings:
    database_url: str = "sqlite:///./radar.db"
    telco_url: str = "http://localhost:8000"
    verified_senders_path: Path = _DEFAULT_VERIFIED
    verified_callers_path: Path = _DEFAULT_CALLERS

    # Confirmation rule (backend/CLAUDE.md)
    min_devices: int = 3          # at least this many independent devices
    min_score: float = 2.4        # and the trust-weighted votes must add up to this
    window_s: int = 1800          # only reports this recent count
    max_reports_per_device_per_hour: int = 30
    suspect_devices: int = 2      # this many phones agreeing makes a sender "suspected"
    fingerprint_distance: int = 6  # Hamming distance that counts as "same campaign"

    # Trust
    new_device_trust: float = 0.3         # where every device starts
    # Devices that send no integrity token START at new_device_trust and can EARN trust (up to this ceiling)
    # only through reports that end up confirmed. Decision of 2026-10-07; the spec's literal 0.3 made
    # confirmation impossible in the hackathon, where no phone has a Play Integrity token.
    no_token_trust_cap: float = 1.0
    # DEMO ONLY: devices whose id starts with this prefix start with `demo_trusted_trust`, standing in for
    # phones that already have a good history. Empty (off) by default. Needed because trust is earned
    # through confirmations, and a brand-new radar has none yet.
    demo_trusted_prefix: str = ""
    demo_trusted_trust: float = 0.8
    unproven_score_cap: float = 1.2       # all unproven devices together can add at most this much
    trust_gain: float = 0.1               # after a report ends up confirmed
    trust_loss: float = 0.05              # after a report on a sender that never got confirmed

    # Poisoning alarm: this many first-time devices on one sender inside this many seconds
    poison_burst_devices: int = 10
    poison_burst_window_s: int = 60

    # Fraud intelligence (contract 5.12). Empty URL = off: the gateway works exactly as before.
    fraudintel_url: str = ""
    intel_sync_s: float = 30.0

    # Private lookups and hashed sync (contracts 5.10, 5.11)
    lookup_rate_per_hour: int = 120  # per device; a phone needs a handful a day, a sweep needs millions
    sync_recent_days: int = 30       # the offline list only holds numbers confirmed this recently

    # Daraja SANDBOX (root principle 10: never live credentials). Empty key = STK Push switched off.
    daraja_consumer_key: str = ""
    daraja_consumer_secret: str = ""
    daraja_shortcode: str = "174379"   # Safaricom's public sandbox test shortcode
    daraja_passkey: str = ""
    daraja_callback_url: str = ""
    # Only these phones (the team's, 2547XXXXXXXX, comma separated) may receive a demo payment prompt, so nobody
    # can use the public gateway to send payment prompts to strangers. Empty = nobody.
    daraja_allowed_payers: tuple = ()

    # The fraud-team console (contract 5.15): off unless switched on. When `fraud_team_key` is set, every console
    # request must carry it in X-Fraud-Team-Key; Caddy adds it after the presenter password, so a browser never sees it.
    fraud_team_console: bool = False
    fraud_team_key: str = ""

    # Telling the network a sender is confirmed
    notify_retries: int = 3
    notify_backoff_s: float = 1.0

    @classmethod
    def from_env(cls) -> "Settings":
        e = os.environ
        d = cls()
        return cls(
            database_url=e.get("DATABASE_URL", d.database_url),
            telco_url=e.get("TELCO_URL", d.telco_url),
            fraudintel_url=e.get("FRAUDINTEL_URL", d.fraudintel_url).rstrip("/"),
            intel_sync_s=float(e.get("INTEL_SYNC_S", d.intel_sync_s)),
            lookup_rate_per_hour=int(e.get("LOOKUP_RATE_PER_HOUR", d.lookup_rate_per_hour)),
            sync_recent_days=int(e.get("SYNC_RECENT_DAYS", d.sync_recent_days)),
            verified_senders_path=Path(e.get("VERIFIED_SENDERS_PATH", str(d.verified_senders_path))),
            verified_callers_path=Path(e.get("VERIFIED_CALLERS_PATH", str(d.verified_callers_path))),
            min_devices=int(e.get("RADAR_MIN_DEVICES", d.min_devices)),
            min_score=float(e.get("RADAR_MIN_SCORE", d.min_score)),
            window_s=int(e.get("RADAR_WINDOW_S", d.window_s)),
            no_token_trust_cap=float(e.get("RADAR_NO_TOKEN_TRUST_CAP", d.no_token_trust_cap)),
            demo_trusted_prefix=e.get("RADAR_DEMO_TRUSTED_PREFIX", d.demo_trusted_prefix),
            demo_trusted_trust=float(e.get("RADAR_DEMO_TRUSTED_TRUST", d.demo_trusted_trust)),
            fraud_team_console=e.get("RADAR_FRAUD_TEAM_CONSOLE", "") in ("1", "true", "yes"),
            fraud_team_key=e.get("FRAUD_TEAM_KEY", ""),
            daraja_consumer_key=e.get("DARAJA_CONSUMER_KEY", ""),
            daraja_consumer_secret=e.get("DARAJA_CONSUMER_SECRET", ""),
            daraja_shortcode=e.get("DARAJA_SHORTCODE", d.daraja_shortcode) or d.daraja_shortcode,
            daraja_passkey=e.get("DARAJA_PASSKEY", ""),
            daraja_callback_url=e.get("DARAJA_CALLBACK_URL", ""),
            daraja_allowed_payers=tuple(p.strip() for p in e.get("DARAJA_ALLOWED_PAYERS", "").split(",") if p.strip()),
        )
