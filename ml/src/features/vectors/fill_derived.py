"""Fills the computed fields of shared/test-vectors.json from the Python code.

Hand-written fields (text, sender, expected_label, ...) are never touched. Computed:
expected_normalized and expected_fingerprint. Run from the repo root:

    python -I ml/src/features/vectors/fill_derived.py          # rewrite the file
    python -I ml/src/features/vectors/fill_derived.py --check  # exit 1 if out of date

Score ranges (expected_score_min/max) come from the current model in shared/models/ (written
by the model export): the model's score for the message, plus or minus 0.001, which is the
tolerance the Kotlin port must meet. With no model yet they stay provisional.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "ml"))

from src.features.fingerprint.simhash import simhash64  # noqa: E402
from src.features.normalize.normalize import normalize  # noqa: E402
from src.features.scoring.scorer import Scorer  # noqa: E402

VECTORS_PATH = ROOT / "shared" / "test-vectors.json"


def current_scorer() -> Scorer | None:
    models = sorted((ROOT / "shared" / "models").glob("model-*.json"))
    return Scorer.from_file(models[-1]) if models else None


def fill(data: dict) -> dict:
    scorer = current_scorer()
    for v in data["vectors"]:
        if scorer is not None:
            score = scorer.score(v["text"], v["sender"])
            v["expected_score_min"] = round(max(0.0, score - 0.001), 4)
            v["expected_score_max"] = round(min(1.0, score + 0.001), 4)
        v["expected_normalized"] = normalize(v["text"])
        v["expected_fingerprint"] = simhash64(v["expected_normalized"])
    return data


def main() -> int:
    data = json.loads(VECTORS_PATH.read_text(encoding="utf-8"))
    new_text = json.dumps(fill(data), indent=2, ensure_ascii=False) + "\n"
    if "--check" in sys.argv:
        return 0 if new_text == VECTORS_PATH.read_text(encoding="utf-8") else 1
    VECTORS_PATH.write_text(new_text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
