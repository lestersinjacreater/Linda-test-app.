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
    real_phones: tuple[str, ...] = ()  # numbers of real handsets to include (wired up in a later step)
    device_prefix: str = "5111"      # must equal the radar's RADAR_DEMO_TRUSTED_PREFIX (demo only)
    linda_phones: int = 30
    smartphones: int = 90            # smartphones without Linda
    feature_phones: int = 180
    blast_recipients: int = 240
    blast_duration_s: float = 90.0   # simulated seconds over which the blast is delivered
    confirm_wait_s: float = 5.0      # real seconds to wait for the radar's confirmation callback
    network_latency_s: float = 1.0   # simulated seconds from the radar's confirm to the telco acting on it

    @classmethod
    def from_env(cls) -> "Settings":
        e, d = os.environ, cls()
        phones = tuple(p.strip() for p in e.get("REAL_PHONES", "").split(",") if p.strip())
        return cls(
            radar_url=e.get("RADAR_URL", d.radar_url),
            sim_speed=float(e.get("SIM_SPEED", d.sim_speed)),
            sim_seed=int(e.get("SIM_SEED", d.sim_seed)),
            model_path=Path(e.get("MODEL_PATH", str(d.model_path))),
            real_phones=phones,
            device_prefix=e.get("SIM_DEVICE_PREFIX", d.device_prefix),
        )
