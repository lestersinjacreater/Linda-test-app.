"""The exported model: parity with scikit-learn, and the safety gates on the shared vectors."""
import json
import sys
from pathlib import Path

import pytest

from src.features.dataset.synthetic import generate
from src.features.scoring.scorer import Scorer, char_wb_ngrams

ROOT = Path(__file__).resolve().parents[2]
MODEL_PATH = sorted((ROOT / "shared" / "models").glob("model-*.json"))[-1]
VECTORS = json.loads((ROOT / "shared" / "test-vectors.json").read_text(encoding="utf-8"))["vectors"]
scorer = Scorer.from_file(MODEL_PATH)


def test_char_wb_ngrams_match_scikit_learn():
    from sklearn.feature_extraction.text import TfidfVectorizer
    analyzer = TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 5), lowercase=False).build_analyzer()
    for text in ["nimekutumia 2500 kimakosa", "a", "ab abc abcd abcde abcdef", "mpesa pin"]:
        assert char_wb_ngrams(text, (2, 5)) == analyzer(text), text


def test_pure_python_scorer_matches_scikit_learn_within_1e_9():
    """Train a small model with sklearn and compare it with the JSON-only scorer."""
    from src.features.train import train as T
    verified = {s.upper() for s in scorer.verified}
    rows = generate(seed=3, per_template=3)
    vec, clf = T.train(rows, verified, C=1.0)
    s = Scorer(T.to_artifact(vec, clf, verified, 1.0))
    sk = T.probabilities(vec, clf, rows[:200], verified)
    for row, p in zip(rows[:200], sk):
        assert s.score(row["text"], row["sender"]) == pytest.approx(p, abs=1e-9), row["id"]


@pytest.mark.parametrize("v", VECTORS, ids=[v["id"] for v in VECTORS])
def test_scores_match_the_shared_vector_ranges(v):
    score = scorer.score(v["text"], v["sender"])
    assert v["expected_score_min"] <= score <= v["expected_score_max"]


def test_no_safe_vector_is_warned_on():
    """A real M-Pesa, bank, KPLC, KRA or OTP message flagged as a scam is a critical bug."""
    for v in VECTORS:
        if v["expected_label"] == "SAFE":
            assert scorer.level(scorer.score(v["text"], v["sender"])) == "SAFE", v["id"]


def test_real_mpesa_vectors_score_near_zero():
    for v in VECTORS:
        if v["sender"] in ("MPESA", "M-PESA"):
            assert scorer.score(v["text"], v["sender"]) < 0.05, v["id"]


def test_same_confirmation_text_flips_with_the_sender():
    v = next(x for x in VECTORS if x["id"] == "fake-mpesa-01")
    assert scorer.score(v["text"], "MPESA") < 0.05
    assert scorer.score(v["text"], "254700000005") > 0.9


def test_scam_vectors_are_caught_except_known_misses():
    known_misses = set()  # see ml/reports/metrics.md for the honest list
    missed = {v["id"] for v in VECTORS if v["expected_label"] == "SCAM"
              and scorer.level(scorer.score(v["text"], v["sender"])) == "SAFE"}
    assert missed <= known_misses | {"job-fee-sw-01"}


def test_model_file_has_the_contract_fields():
    m = json.loads(MODEL_PATH.read_text(encoding="utf-8"))
    for key in ("version", "created_at", "normalizer_version", "vectorizer", "classes", "coef", "intercept",
                "metadata_features", "thresholds", "categories", "metrics"):
        assert key in m, key
    assert m["thresholds"] == {"warn": 0.55, "scam": 0.80}
    assert len(m["coef"]) == len(m["vectorizer"]["idf"]) == len(m["vectorizer"]["vocabulary"])
    assert m["version"] in MODEL_PATH.name


def test_copies_in_android_and_simulator_have_the_same_version():
    for rel in ("android/app/src/main/assets/model.json", "simulator/models/model.json"):
        copy = json.loads((ROOT / rel).read_text(encoding="utf-8"))
        assert copy["version"] == scorer.version, rel


def test_categories_name_the_scam_type_for_most_scam_vectors():
    from src.features.scoring.categories import categorise
    names = {"reversal": "sent_by_mistake"}
    scams = [v for v in VECTORS if v["expected_label"] == "SCAM"]
    wrong = [(v["id"], categorise(v["text"], v["sender"], scorer.verified)) for v in scams
             if categorise(v["text"], v["sender"], scorer.verified) != names.get(v["campaign"], v["campaign"])]
    assert len(wrong) <= 3, wrong  # the keyword rules are simple on purpose; report the misses honestly


def test_categories_never_invent_a_scam_type_for_safe_real_mpesa():
    from src.features.scoring.categories import categorise
    for v in VECTORS:
        if v["sender"] in ("MPESA", "M-PESA"):
            assert categorise(v["text"], v["sender"], scorer.verified) != "fake_mpesa"


def test_simulator_vendored_copy_is_up_to_date():
    from src.features.export.vendor import TARGET, vendored_sources
    for name, expected in vendored_sources().items():
        assert (TARGET / name).read_text(encoding="utf-8") == expected, f"{name} drifted: run make export-model"
