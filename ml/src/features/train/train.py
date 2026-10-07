"""Trains the baseline: character n-gram TF-IDF + context features -> logistic regression.

Why this model: it is small (a JSON file), fast on a cheap phone, needs no ML runtime in the
APK, and every score can be explained by listing the n-grams that pushed it up. Reproducible:
every random choice is seeded.

Run from the repo root:  python -I ml/src/features/train/train.py
"""
import json
import random
import sys
from datetime import date
from pathlib import Path

import numpy as np
from scipy.sparse import csr_matrix, hstack
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "ml"))

from src.features.dataset.augment import obfuscate  # noqa: E402
from src.features.dataset.synthetic import generate, read_csv, write_csv  # noqa: E402
from src.features.normalize.normalize import NORMALIZER_VERSION, normalize  # noqa: E402
from src.features.scoring.metadata import FEATURE_NAMES, metadata_features  # noqa: E402

MODEL_VERSION = "2026.10.07-1"
SEED = 42
NGRAM_RANGE = (2, 5)
THRESHOLDS = {"warn": 0.55, "scam": 0.80}
DATA_PATH = ROOT / "ml" / "data" / "processed" / "synthetic.csv"
ARTIFACT_PATH = ROOT / "ml" / "artifacts" / "model.json"
VERIFIED_PATH = ROOT / "shared" / "verified_senders.json"


def load_verified() -> set[str]:
    return {s.upper() for s in json.loads(VERIFIED_PATH.read_text(encoding="utf-8"))["sender_ids"]}


def load_rows() -> list[dict]:
    if not DATA_PATH.exists():
        write_csv(DATA_PATH, generate())
    return read_csv(DATA_PATH)


def add_obfuscated_copies(rows: list[dict], seed: int = SEED) -> list[dict]:
    """Add disguised copies of half the scams and a tenth of the legit messages."""
    rng = random.Random(seed)
    extra = []
    for r in rows:
        if rng.random() < (0.5 if r["label"] == "scam" else 0.1):
            extra.append({**r, "id": r["id"] + "-obf", "text": obfuscate(r["text"], rng)})
    return rows + extra


def design_matrix(rows, vectorizer, verified, fit=False):
    texts = [normalize(r["text"]) for r in rows]
    tfidf = vectorizer.fit_transform(texts) if fit else vectorizer.transform(texts)
    meta = csr_matrix(np.array([metadata_features(r["text"], r["sender"], verified) for r in rows]))
    return hstack([tfidf, meta]).tocsr()


def train(train_rows: list[dict], verified: set[str], C: float = 1.0):
    """Returns (vectorizer, classifier). Only ever call this with training rows."""
    vectorizer = TfidfVectorizer(
        analyzer="char_wb", ngram_range=NGRAM_RANGE, sublinear_tf=True, norm="l2",
        lowercase=False, min_df=3, max_features=20000, dtype=np.float64,
    )
    X = design_matrix(train_rows, vectorizer, verified, fit=True)
    y = np.array([1 if r["label"] == "scam" else 0 for r in train_rows])
    clf = LogisticRegression(C=C, class_weight="balanced", max_iter=2000, solver="liblinear", random_state=SEED)
    clf.fit(X, y)
    return vectorizer, clf


def probabilities(vectorizer, clf, rows, verified) -> np.ndarray:
    return clf.predict_proba(design_matrix(rows, vectorizer, verified))[:, 1]


def choose_C(train_rows, val_rows, verified) -> float:
    """Pick the regularisation strength that does best on validation (log loss)."""
    from sklearn.metrics import log_loss
    y = [1 if r["label"] == "scam" else 0 for r in val_rows]
    best = min(
        (log_loss(y, probabilities(*train(train_rows, verified, C), val_rows, verified)), C)
        for C in (0.5, 1.0, 2.0, 5.0, 10.0)
    )
    return best[1]


def to_artifact(vectorizer, clf, verified: set[str], C: float, metrics: dict | None = None) -> dict:
    """The export contract (root CLAUDE.md 5.8)."""
    n_text = len(vectorizer.vocabulary_)
    coef = clf.coef_[0]
    return {
        "version": MODEL_VERSION,
        "created_at": date.today().isoformat(),
        "normalizer_version": NORMALIZER_VERSION,
        "vectorizer": {
            "analyzer": "char_wb", "ngram_range": list(NGRAM_RANGE), "sublinear_tf": True, "norm": "l2",
            "vocabulary": {k: int(v) for k, v in sorted(vectorizer.vocabulary_.items(), key=lambda kv: kv[1])},
            "idf": [float(x) for x in vectorizer.idf_],
        },
        "classes": ["legit", "scam"],
        "coef": [float(x) for x in coef[:n_text]],
        "intercept": float(clf.intercept_[0]),
        "metadata_features": [{"name": n, "coef": float(c)} for n, c in zip(FEATURE_NAMES, coef[n_text:])],
        "verified_senders": sorted(verified),
        "thresholds": THRESHOLDS,
        "categories": {},
        "metrics": metrics or {},
        "training": {"data": "synthetic (no real data yet)", "C": C, "seed": SEED},
    }


def main() -> None:
    verified = load_verified()
    rows = load_rows()
    train_rows = add_obfuscated_copies([r for r in rows if r["split"] == "train"])
    val_rows = [r for r in rows if r["split"] == "val"]
    C = choose_C(train_rows, val_rows, verified)
    vectorizer, clf = train(train_rows, verified, C)  # validation rows are NOT trained on
    ARTIFACT_PATH.parent.mkdir(parents=True, exist_ok=True)
    ARTIFACT_PATH.write_text(json.dumps(to_artifact(vectorizer, clf, verified, C)), encoding="utf-8")
    print(f"trained on {len(train_rows)} rows, C={C}, vocabulary={len(vectorizer.vocabulary_)}")
    print(f"wrote {ARTIFACT_PATH}")


if __name__ == "__main__":
    main()
