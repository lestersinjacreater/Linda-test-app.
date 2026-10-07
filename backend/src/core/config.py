"""All tunable numbers in one place, read from environment variables (never hardcoded secrets)."""
import os
from dataclasses import dataclass
from pathlib import Path

_DEFAULT_VERIFIED = Path(__file__).resolve().parents[3] / "shared" / "verified_senders.json"


@dataclass(frozen=True)
class Settings:
    database_url: str = "sqlite:///./radar.db"
    telco_url: str = "http://localhost:8000"
    verified_senders_path: Path = _DEFAULT_VERIFIED

    # Confirmation rule (backend/CLAUDE.md)
    min_devices: int = 3          # at least this many independent devices
    min_score: float = 2.4        # and the trust-weighted votes must add up to this
    window_s: int = 1800          # only reports this recent count
    max_reports_per_device_per_hour: int = 30
    suspect_devices: int = 2      # this many phones agreeing makes a sender "suspected"
    fingerprint_distance: int = 6  # Hamming distance that counts as "same campaign"

    # Trust
    new_device_trust: float = 0.3         # where every device starts
    no_token_trust_cap: float = 0.3       # ceiling for devices that send no integrity token (spec: <= 0.3)
    unproven_score_cap: float = 1.2       # all unproven devices together can add at most this much
    trust_gain: float = 0.1               # after a report ends up confirmed
    trust_loss: float = 0.05              # after a report on a sender that never got confirmed

    # Poisoning alarm: this many first-time devices on one sender inside this many seconds
    poison_burst_devices: int = 10
    poison_burst_window_s: int = 60

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
            verified_senders_path=Path(e.get("VERIFIED_SENDERS_PATH", str(d.verified_senders_path))),
            min_devices=int(e.get("RADAR_MIN_DEVICES", d.min_devices)),
            min_score=float(e.get("RADAR_MIN_SCORE", d.min_score)),
            window_s=int(e.get("RADAR_WINDOW_S", d.window_s)),
            no_token_trust_cap=float(e.get("RADAR_NO_TOKEN_TRUST_CAP", d.no_token_trust_cap)),
        )
