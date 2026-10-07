# Contracts

The authoritative list of API contracts is section 5 of the root CLAUDE.md.
Any change: update CLAUDE.md section 5, every component that uses the contract, and its tests,
in one commit, with the message prefix `contract:`.

## Normalizer rules (5.9, implemented in ml/src/features/normalize/normalize.py)
1. Lowercase A-Z only (ASCII), so Python and Kotlin agree.
2. Undo leetspeak (3 0 1 4 5 @ $) only inside words: a run of such characters is swapped when letters are on both
   sides, when it is only @/$ touching a letter, or when it is a single character at a word edge. Amounts such as
   `2,500`, `Ksh3,140`, `Ksh500` are left alone. 10-character M-Pesa codes (capitals+digits) are never swapped.
3. `m pesa`, `m.pesa`, `m-pesa` become `mpesa`. Three or more single letters separated by one space, dot, dash
   or underscore are joined (`t u m a` becomes `tuma`).
4. Commas between digits are removed (`2,500` becomes `2500`); every other non `a-z0-9` run becomes one space.

## SimHash (5.1 `fingerprint`, ml/src/features/fingerprint/simhash.py)
64-bit; pieces are the overlapping character 3-grams of the normalised text (counted, not deduplicated);
each piece is hashed with FNV-1a 64 (offset 0xcbf29ce484222325, prime 0x100000001b3) over its ASCII bytes;
bit b of the result is 1 when the sum over pieces of (+count if bit b is set, else -count) is positive.
Text shorter than 3 characters is one piece; empty text gives `0000000000000000`. Output is 16 lowercase hex chars.

## Model artifact details (5.8, implemented in ml/src/features/train/train.py and scoring/scorer.py)
- `vectorizer`: `analyzer` `char_wb`, `ngram_range` [2,5], `sublinear_tf`, `norm` `l2`, `vocabulary` (n-gram to column), `idf`.
  Scored on the NORMALISED text (no lowercasing inside the vectorizer).
- `char_wb` n-grams: for each whitespace-separated word, pad one space on each side; for each n from 2 to 5 take every
  window of n characters (a word shorter than n gives its padded form once, then longer n are skipped).
- Weight of an n-gram in the vocabulary: `(1 + ln(count)) * idf`; then divide all weights by their L2 norm
  (n-grams outside the vocabulary are ignored, and an empty vector stays empty).
- `metadata_features`: ordered list of `{name, coef}` for five 0/1 features computed on the RAW text and sender
  (`sender_verified`, `sender_personal_number`, `has_link`, `mpesa_style`, `fake_mpesa`; exact rules in
  ml/src/features/scoring/metadata.py). `verified_senders` is embedded so every platform uses the same list.
- score = sigmoid(intercept + sum(coef[i] * weight[i]) + sum(meta_coef * meta_value)). Kotlin must match to within 0.001.
- Level: `scam` if score >= thresholds.scam (0.80), `caution` if >= thresholds.warn (0.55), else `safe`.
- `shared/test-vectors.json` scores are computed WITH the vector's sender, because the sender is a model input.

## Simulator to dashboard additions (5.5)
- `GET /population` returns `[{phone, kind, town, lat, lon}]` for every simulated phone (same in every run), so idle phones can be drawn before anything happens.
- `GET /history` returns every event of the current run (used to record the dashboard's offline replay: `make record-fallback`).
- Every scenario starts with a `reset` event and a cleared history, so a dashboard that sees `reset` clears its map and counters.
- Events carry no message text. Browser calls are allowed from any origin (CORS `*`): the simulator is demo infrastructure with no user data.
