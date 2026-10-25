"""Trains the model: hashed character n-gram TF-IDF + context features -> logistic regression.

Why this model: it is tiny (about 87 KB), fast on a cheap phone, needs no ML runtime in the
APK, and every score can be explained by listing the n-grams that pushed it up. Reproducible:
every random choice is seeded.

Lightweight (root CLAUDE.md 5.8, 7.1): letter pieces are hashed into BUCKETS slots instead of
kept in a vocabulary, the idf is stored as 1 byte per slot (value = byte / IDF_SCALE) and the
weights as 1 signed byte per slot (value = byte * coef_scale). Training uses the 1-byte idf, and
every probability reported here uses the 1-byte weights, so the numbers describe the model that
actually ships.

Run from the repo root:  python -I ml/src/features/train/train.py
"""
import json
import random
import sys
from datetime import date
from pathlib import Path

import numpy as np
import base64

from scipy.sparse import csr_matrix, hstack
from sklearn.linear_model import LogisticRegression

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "ml"))

from src.features.dataset.augment import obfuscate  # noqa: E402
from src.features.dataset.synthetic import generate, read_csv, write_csv  # noqa: E402
from src.features.normalize.normalize import NORMALIZER_VERSION, normalize  # noqa: E402
from src.features.scoring.metadata import FEATURE_NAMES, metadata_features  # noqa: E402
from src.features.scoring.scorer import bucket_counts, hashed_weights  # noqa: E402

MODEL_VERSION = "2026.10.25-1"
SEED = 42
NGRAM_RANGE = (2, 5)
BUCKETS = 32768   # 2^15 slots: 32 KB of weights + 32 KB of idf (see ml/reports/metrics.md for the size comparison)
IDF_SCALE = 16    # idf is stored as round(idf * 16) in one byte (idf here is at most about 10)
MIN_DF = 3        # like the old vocabulary: a slot seen in fewer than 3 training messages is ignored (idf 0)
THRESHOLDS = {"warn": 0.55, "scam": 0.80}
DATA_PATH = ROOT / "ml" / "data" / "processed" / "synthetic.csv"
# Real data, used when present (both are git-ignored and rebuilt locally, see ml/data/README.md):
PUBLIC_PATH = ROOT / "ml" / "data" / "processed" / "public.csv"  # Bongoscam + Mendeley
TEAM_PATH = ROOT / "ml" / "data" / "processed" / "team_collected.csv"  # scrubbed Kenyan messages
KENYA_PATH = ROOT / "ml" / "data" / "processed" / "kenya_sms.csv"  # Mwatsefu/IwazoLab Kenyan SMS, our labels
ARTIFACT_PATH = ROOT / "ml" / "artifacts" / "model.json"
VERIFIED_PATH = ROOT / "shared" / "verified_senders.json"


def load_verified() -> set[str]:
    return {s.upper() for s in json.loads(VERIFIED_PATH.read_text(encoding="utf-8"))["sender_ids"]}


def load_rows() -> list[dict]:
    """Synthetic rows always; real public, Kenyan and team rows whenever their files exist."""
    if not DATA_PATH.exists():
        write_csv(DATA_PATH, generate())
    rows = read_csv(DATA_PATH)
    for path in (PUBLIC_PATH, KENYA_PATH, TEAM_PATH):
        if path.exists():
            rows += read_csv(path)
    return rows


def data_description(rows: list[dict]) -> str:
    """One line for the model file saying what it was trained on."""
    counts = {}
    for r in rows:
        if r["split"] in ("train", "val"):
            counts[r["source"]] = counts.get(r["source"], 0) + 1
    return ", ".join(f"{k} {v}" for k, v in sorted(counts.items()))


def add_obfuscated_copies(rows: list[dict], seed: int = SEED) -> list[dict]:
    """Add disguised copies of half the scams and a tenth of the legit messages."""
    rng = random.Random(seed)
    extra = []
    for r in rows:
        if rng.random() < (0.5 if r["label"] == "scam" else 0.1):
            extra.append({**r, "id": r["id"] + "-obf", "text": obfuscate(r["text"], rng)})
    return rows + extra


class HashedVectorizer:
    """Stands in for scikit-learn's TfidfVectorizer, but with hashed slots and a 1-byte idf."""

    def __init__(self, buckets: int = BUCKETS, ngram_range: tuple[int, int] = NGRAM_RANGE):
        self.buckets, self.ngram_range = buckets, ngram_range
        self.idf_bytes: np.ndarray | None = None

    def fit(self, texts: list[str]) -> "HashedVectorizer":
        df = np.zeros(self.buckets)
        for t in texts:
            for b in bucket_counts(t, self.ngram_range, self.buckets):
                df[b] += 1
        idf = np.log((1 + len(texts)) / (1 + df)) + 1.0  # scikit-learn's smooth idf
        idf[df < MIN_DF] = 0.0  # rare pieces are noise that only fits the training campaigns
        self.idf_bytes = np.clip(np.round(idf * IDF_SCALE), 0, 255).astype(np.uint8)
        return self

    @property
    def idf(self) -> list[float]:
        return [int(q) / IDF_SCALE for q in self.idf_bytes]

    def transform(self, texts: list[str]) -> csr_matrix:
        idf = self.idf
        data, cols, ptr = [], [], [0]
        for t in texts:
            w = hashed_weights(t, self.ngram_range, self.buckets, idf)  # the scorer's exact function
            cols += list(w)
            data += list(w.values())
            ptr.append(len(cols))
        return csr_matrix((data, cols, ptr), shape=(len(texts), self.buckets), dtype=np.float64)


def design_matrix(rows, vectorizer, verified, fit=False):
    texts = [normalize(r["text"]) for r in rows]
    if fit:
        vectorizer.fit(texts)
    meta = csr_matrix(np.array([metadata_features(r["text"], r["sender"], verified) for r in rows]))
    return hstack([vectorizer.transform(texts), meta]).tocsr()


def train(train_rows: list[dict], verified: set[str], C: float = 1.0):
    """Returns (vectorizer, classifier). Only ever call this with training rows."""
    vectorizer = HashedVectorizer()
    X = design_matrix(train_rows, vectorizer, verified, fit=True)
    y = np.array([1 if r["label"] == "scam" else 0 for r in train_rows])
    clf = LogisticRegression(C=C, class_weight="balanced", max_iter=3000, solver="liblinear", random_state=SEED)
    clf.fit(X, y)
    return vectorizer, clf


def quantized_coef(vectorizer, clf) -> tuple[np.ndarray, float]:
    """The slot weights as signed bytes plus one scale: weight = byte * scale."""
    w = clf.coef_[0][: vectorizer.buckets]
    scale = float(np.abs(w).max() / 127) or 1.0
    return np.clip(np.round(w / scale), -127, 127).astype(np.int8), scale


def probabilities(vectorizer, clf, rows, verified) -> np.ndarray:
    """Probabilities of the model AS SHIPPED (1-byte weights), not of the float model."""
    q, scale = quantized_coef(vectorizer, clf)
    coef = np.concatenate([q.astype(np.float64) * scale, clf.coef_[0][vectorizer.buckets :]])
    z = design_matrix(rows, vectorizer, verified) @ coef + clf.intercept_[0]
    return 1.0 / (1.0 + np.exp(-z))


def choose_C(train_rows, val_rows, verified) -> float:
    """Pick the regularisation strength that does best on validation (log loss)."""
    from sklearn.metrics import log_loss
    y = [1 if r["label"] == "scam" else 0 for r in val_rows]
    best = min(
        (log_loss(y, probabilities(*train(train_rows, verified, C), val_rows, verified)), C)
        for C in (0.5, 1.0, 2.0, 5.0, 10.0)
    )
    return best[1]


def to_artifact(vectorizer, clf, verified: set[str], C: float, metrics: dict | None = None,
                data: str = "synthetic (no real data yet)") -> dict:
    """The export contract (root CLAUDE.md 5.8)."""
    n_text = vectorizer.buckets
    coef = clf.coef_[0]
    q, scale = quantized_coef(vectorizer, clf)
    return {
        "version": MODEL_VERSION,
        "created_at": date.today().isoformat(),
        "normalizer_version": NORMALIZER_VERSION,
        "vectorizer": {
            "analyzer": "char_wb", "ngram_range": list(NGRAM_RANGE), "sublinear_tf": True, "norm": "l2",
            "hash": "fnv1a_64", "buckets": n_text, "idf_scale": IDF_SCALE,
            "idf_uint8_b64": base64.b64encode(vectorizer.idf_bytes.tobytes()).decode("ascii"),
        },
        "classes": ["legit", "scam"],
        "coef_scale": scale,
        "coef_int8_b64": base64.b64encode(q.tobytes()).decode("ascii"),
        "intercept": float(clf.intercept_[0]),
        "metadata_features": [{"name": n, "coef": float(c)} for n, c in zip(FEATURE_NAMES, coef[n_text:])],
        "verified_senders": sorted(verified),
        "thresholds": THRESHOLDS,
        "categories": {},
        "metrics": metrics or {},
        "training": {"data": data, "C": C, "seed": SEED},
    }


def main() -> None:
    verified = load_verified()
    rows = load_rows()
    train_rows = add_obfuscated_copies([r for r in rows if r["split"] == "train"])
    val_rows = [r for r in rows if r["split"] == "val"]
    C = choose_C(train_rows, val_rows, verified)
    vectorizer, clf = train(train_rows, verified, C)  # validation rows are NOT trained on
    ARTIFACT_PATH.parent.mkdir(parents=True, exist_ok=True)
    ARTIFACT_PATH.write_text(json.dumps(to_artifact(vectorizer, clf, verified, C, data=data_description(rows))), encoding="utf-8")
    print(f"trained on {len(train_rows)} rows, C={C}, buckets={vectorizer.buckets}")
    print(f"wrote {ARTIFACT_PATH}")


if __name__ == "__main__":
    main()
