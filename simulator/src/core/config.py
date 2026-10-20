"""Simulator settings, read from environment variables (see .env.example)."""
import os
from dataclasses import dataclass, field
from pathlib import Path

_HERE = Path(__file__).resolve().parents[2]


@dataclass(frozen=True)
class Settings:
    radar_url: str = "http://localhost:8001"
    sim_speed: float = 20.0          # simulated seconds per real second; 0 = as fast as possible (tests)
    sim_seed: int = 42               # same seed, same demo
    model_path: Path = _HERE / "models" / "model.json"
    real_phones: tuple[str, ...] = ()  # the team's own basic phones (REAL_PHONES, from .env only, never committed)
    device_prefix: str = "5111"      # must equal the radar's RADAR_DEMO_TRUSTED_PREFIX (demo only)
    linda_phones: int = 30
    smartphones: int = 90            # smartphones without Linda
    feature_phones: int = 180
    blast_recipients: int = 240
    blast_duration_s: float = 90.0   # simulated seconds over which the blast is delivered
    confirm_wait_s: float = 5.0      # real seconds to wait for the radar's confirmation callback
    network_latency_s: float = 1.0   # simulated seconds from the radar's confirm to the telco acting on it

    # Adoption levels (?adoption=0.5|1|2): a bigger country so a realistic share can run Linda
    adoption_population: int = 2000
    adoption_recipients: int = 500   # "a scam blast to 500 people" (root CLAUDE.md 1)
    feature_share: float = 0.6       # basic phones among all phones: the classic demo's mix (180 of 300). An assumption.

    # Scam calls (POST /scenarios/call)
    call_share: float = 0.5          # the scammer phones every second SMS recipient ("did you get my money?")
    cold_calls: int = 40             # plus people who never got the SMS
    call_after_sms_s: tuple[float, float] = (30.0, 120.0)
    call_length_s: tuple[float, float] = (20.0, 90.0)

    # Africa's Talking, for warnings to the real phones. Empty key: nothing is sent (the demo still runs).
    at_username: str = "sandbox"     # "sandbox" only reaches AT's web simulator; a live account reaches real SIMs
    at_api_key: str = ""
    at_sender_id: str = ""

    @classmethod
    def from_env(cls) -> "Settings":
        e, d = os.environ, cls()
        phones = tuple(to_msisdn(p) for p in e.get("REAL_PHONES", "").split(",") if p.strip())
        return cls(
            radar_url=e.get("RADAR_URL", d.radar_url),
            sim_speed=float(e.get("SIM_SPEED", d.sim_speed)),
            sim_seed=int(e.get("SIM_SEED", d.sim_seed)),
            model_path=Path(e.get("MODEL_PATH", str(d.model_path))),
            real_phones=phones,
            device_prefix=e.get("SIM_DEVICE_PREFIX", d.device_prefix),
            feature_share=float(e.get("SIM_FEATURE_SHARE", d.feature_share)),
            at_username=e.get("AT_USERNAME") or d.at_username,
            at_api_key=e.get("AT_API_KEY", d.at_api_key),
            at_sender_id=e.get("AT_SENDER_ID", d.at_sender_id),
        )


def to_msisdn(raw: str) -> str:
    """0712 345 678, +254712345678 or 254712345678 -> 254712345678 (the system's format)."""
    digits = "".join(c for c in raw if c.isdigit())
    if digits.startswith("0") and len(digits) == 10:
        digits = "254" + digits[1:]
    if not (len(digits) == 12 and digits.startswith("254")):
        raise ValueError(f"REAL_PHONES has a number that is not a Kenyan mobile number: {raw!r}")
    return digits
