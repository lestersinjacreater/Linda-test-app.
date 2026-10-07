# ml/: dataset, training, evaluation, export

The dataset pipeline already exists (`src/features/dataset/`, see ml/README.md). Do not rewrite it;
extend it.

## To build
- `src/features/train/train.py` — char_wb TF-IDF (2–5) + metadata features + LogisticRegression
  (class_weight balanced). Train on `split == train`, tune on `val`. Seeded and reproducible.
- `src/features/evaluate/evaluate.py` — on `split == test` only: precision, recall, F1, PR curve,
  false-alarm rate on hard negatives (verified-sender style messages, OTPs, bank alerts), per-category
  recall, robustness set (obfuscated variants). Writes `reports/metrics.md` + `reports/metrics.json`.
- `src/features/export/export.py` — writes `shared/models/model-<version>.json` (root 5.8) and copies
  it to android assets and simulator models. Also regenerates expected scores in
  `shared/test-vectors.json` and fails if Python and stored values disagree.
- `src/features/fingerprint/simhash.py` — 64-bit SimHash over char 3-grams of `text_normalized`.
  Mirror exactly in Kotlin.
- Optional (P1, only if time): small multilingual transformer → TFLite int8; compare with the
  baseline in `reports/model-comparison.md`. Ship whichever wins on the test set and phone speed.

## Rules
- Never train or tune on `split == test`. Never put synthetic data in test.
- Thresholds: choose `warn` and `scam` to keep false alarms on hard negatives near zero; document why.
- Freeze model v1 on 17 Oct. After that, thresholds only.
- Every dataset listed in root `DATASETS.md` with licence and how it was used.
