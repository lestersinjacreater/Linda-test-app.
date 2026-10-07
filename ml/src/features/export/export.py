"""Publishes the trained model (root CLAUDE.md 5.8) and refreshes the shared test vectors.

    python -I ml/src/features/export/export.py

Writes shared/models/model-<version>.json, copies it to the Android assets and the simulator
(both must report the same version), copies the scoring code into the simulator, then rewrites the expected scores in
shared/test-vectors.json from the model, so Python, Kotlin and the simulator are all checked
against the same numbers.
"""
import json
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "ml"))

from src.features.train.train import ARTIFACT_PATH  # noqa: E402

METRICS_PATH = ROOT / "ml" / "reports" / "metrics.json"
SHARED_DIR = ROOT / "shared" / "models"
COPIES = [ROOT / "android" / "app" / "src" / "main" / "assets" / "model.json", ROOT / "simulator" / "models" / "model.json"]


def main() -> None:
    model = json.loads(ARTIFACT_PATH.read_text(encoding="utf-8"))
    if METRICS_PATH.exists():
        m = json.loads(METRICS_PATH.read_text(encoding="utf-8"))
        model["metrics"] = {
            "data": "SYNTHETIC ONLY, not evidence on real scams",
            "validation_f1": round(m["validation"]["f1"], 4),
            "hard_negative_false_alarm_rate": m["validation"]["hard_negative_false_alarm_rate"],
            "held_out_campaign_average": round(m["held_out_campaign_average"], 4),
        }
    text = json.dumps(model, separators=(",", ":"))
    SHARED_DIR.mkdir(parents=True, exist_ok=True)
    for old in SHARED_DIR.glob("model-*.json"):
        old.unlink()  # one current model; git history keeps the old ones
    target = SHARED_DIR / f"model-{model['version']}.json"
    target.write_text(text, encoding="utf-8")
    for copy in COPIES:
        copy.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(target, copy)
    print(f"exported {target.name} ({len(text) // 1024} KB) to shared/, android assets and simulator")
    from src.features.export.vendor import write_vendor
    write_vendor()
    print("refreshed the vendored scoring code in simulator/src/vendor/linda")
    subprocess.run([sys.executable, "-I", str(ROOT / "ml" / "src" / "features" / "vectors" / "fill_derived.py")], check=True)
    print("refreshed expected scores in shared/test-vectors.json")


if __name__ == "__main__":
    main()
