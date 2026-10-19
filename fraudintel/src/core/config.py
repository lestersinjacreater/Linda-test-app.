"""Settings from environment variables."""
import os
from dataclasses import dataclass
from pathlib import Path

_ROOT = Path(__file__).resolve().parents[3]


@dataclass(frozen=True)
class Settings:
    seed_path: Path = _ROOT / "fraudintel" / "data" / "seed.json"
    verified_callers_path: Path = _ROOT / "shared" / "verified_callers.json"
    inbox_limit: int = 500  # most recent reports kept for the demo

    @classmethod
    def from_env(cls) -> "Settings":
        d = cls()
        e = os.environ
        return cls(
            seed_path=Path(e.get("FRAUDINTEL_SEED_PATH", str(d.seed_path))),
            verified_callers_path=Path(e.get("VERIFIED_CALLERS_PATH", str(d.verified_callers_path))),
        )
