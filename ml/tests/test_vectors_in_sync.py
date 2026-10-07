"""expected_normalized / expected_fingerprint must be what fill_derived.py would write."""
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def test_derived_fields_are_up_to_date():
    script = ROOT / "ml" / "src" / "features" / "vectors" / "fill_derived.py"
    result = subprocess.run([sys.executable, "-I", str(script), "--check"])
    assert result.returncode == 0, "run: python -I ml/src/features/vectors/fill_derived.py"
