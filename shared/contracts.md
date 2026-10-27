# Contracts

The authoritative list of API contracts is section 5 of the root CLAUDE.md.
Any change: update CLAUDE.md section 5, every component that uses the contract, and its tests,
in one commit, with the message prefix `contract:`.
The lightweight model format in 5.8
are marked PLANNED in CLAUDE.md: they are the agreed target and are not implemented yet.

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
- `vectorizer`: `analyzer` `char_wb`, `ngram_range` [2,5], `sublinear_tf`, `norm` `l2`, `hash` `fnv1a_64`, `buckets` (32768),
  `idf_scale` (16), `idf_uint8_b64`. Scored on the NORMALISED text (no lowercasing inside the vectorizer).
- `char_wb` n-grams: for each whitespace-separated word, pad one space on each side; for each n from 2 to 5 take every
  window of n characters (a word shorter than n gives its padded form once, then longer n are skipped).
- Slot of an n-gram: FNV-1a 64 (same constants as the fingerprint) over its UTF-8 bytes, read as an UNSIGNED 64-bit
  number, modulo `buckets`. Two n-grams may share a slot; their counts add up.
- `idf[slot]` = byte `slot` of base64-decoded `idf_uint8_b64`, read UNSIGNED (0 to 255), divided by `idf_scale`.
  A slot seen in fewer than 3 training messages has idf 0, so it is ignored.
- Weight of a slot: `(1 + ln(count)) * idf[slot]`; then divide all weights by their L2 norm (an empty vector stays empty).
- `coef[slot]` = byte `slot` of base64-decoded `coef_int8_b64`, read SIGNED (-127 to 127), times `coef_scale`.
  Training reports metrics with these rounded weights, so the numbers describe the shipped file.
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

## Network effect additions (5.5, root CLAUDE.md item 6)
- `?adoption=<percent>` on `POST /scenarios/blast|rotating|call` (0 < percent <= 100): a 2,000-phone country
  (`SIM_FEATURE_SHARE` 0.6 basic phones) and a 500-person blast. Without it, the classic 300-phone demo (10% Linda).
  Same seed: same phones, recipients and timing at every level; only smartphones adopt, and the lower levels' users
  are always inside the higher levels'. The population has its own random stream (`population-<seed>`).
- `POST /scenarios/call`: the SMS blast exactly as `blast`, then the sender phones every second recipient 30 to 120 s
  after their SMS, plus 40 people who never got it. `?caller=2547XXXXXXXX`: calls only, and the telco asks the gateway
  about the caller with the server-side risk lookup (5.2) before the first call.
- On each call: `call_attempt {sender, phone..., known}` (`known` = confirmed by then). A Linda phone emits
  `call_labelled {label, before_answer: true}` with the app's order: `recent_scam_message` (it caught this sender's SMS),
  `reported` (confirmed), `suspected` (gateway said suspected or reported). A phone without Linda that was not already
  warned gets `warning_delivered {channel: "after_call", before_read: false}` right after the call, only if the sender
  was confirmed when the call started. Calls before confirmation reach those phones unprotected.
- `warning_delivered` always has `channel` (`sms` | `after_call`) and `real_sms` (true for a `REAL_PHONES` handset).
- `/metrics` adds `adoption_pct`, `linda_recipients`, `calls` (`placed, to_linda_phones, labelled_before_answer,
  to_other_phones, already_warned_by_sms, warned_after_call, unprotected, protected_pct`, or null) and
  `real_phone_sms` (`[{phone, sender, channel, status: sent|failed|skipped}]`).
- `GET /adoption/odds?levels=0.5,1,2&trials=1000&min_reports=3` -> `{recipients, trials, min_reports, assumes, levels:
  [{adoption_pct, linda_recipients_avg, confirmed_pct, confirmed_pct_binomial, confirmed_after_s_median,
  warned_before_read_pct, warned_before_read_pct_linda_only}]}`. Assumes every Linda recipient catches the scam.
- Real phones (`REAL_PHONES`): basic phones in Nairobi, always among the blast's recipients (a quarter of the way in),
  never called. Only the WARNING is sent, as a real SMS through Africa's Talking (`AT_USERNAME`, `AT_API_KEY`,
  `AT_SENDER_ID`), starting "[Linda demo]"; never the scam text. No key: status `skipped`. Their numbers never appear in events.

## USSD checker (5.6, implemented in backend/src/features/ussd/router.py)

- `POST /ussd`, form fields `sessionId, serviceCode, phoneNumber, networkCode, text`; reply `text/plain`.
- `text` = every answer so far joined by `*`. `""` → the menu (`CON`). `1` → asks for the number (`CON`).
  `1*<number>` → the answer (`END`). `2` → how to stay safe (`END`). `3` → the same menu in Kiswahili; `3*1`, `3*1*<number>`,
  `3*2` as above. Anything else → the menu again.
- The number is typed as `07XX XXX XXX`, `01…`, `+254…`, `254…` or `7XX…`; anything else → `END` asking to dial again.
- The answer uses the risk lookup (5.2): `confirmed` → a warning naming the scam type; `suspected` (a few phones, or the
  fraud team's "reported") → a caution with the report count; `verified` → the caller's name; `unknown` → "no reports yet",
  never "safe". Every reply fits 182 characters.
- Rate limit: `LOOKUP_RATE_PER_HOUR` number checks per `phoneNumber`, then `END` "too many checks".

## Payment precheck (5.7, implemented in backend/src/features/payments/)

- `POST /v1/payments/precheck` `{recipient, amount}` (2547XXXXXXXX, Ksh 1 to 250,000; unknown fields refused) →
  `{allow, status, reason, reason_sw}`. Only `confirmed` gives `allow: false`. `suspected` and `unknown` are allowed with
  a caution, never called "safe"; `verified` names the caller. Rate limit `LOOKUP_RATE_PER_HOUR` per `X-Linda-Device` or
  caller address, then 429.
- `POST /v1/payments/stk` `{payer, recipient, amount}` → `{checkout_request_id, message, precheck}`. 503 when the
  `DARAJA_*` settings are empty, 403 when the payer is not in `DARAJA_ALLOWED_PAYERS` or the recipient is confirmed (no
  prompt is sent), 502 when the sandbox refuses. The host is fixed to `sandbox.safaricom.co.ke` in code.
- `POST /v1/payments/stk/callback`: the sandbox's result; answered `{ResultCode: 0}`, nothing stored.

## Real numbers for the dashboard (5.14, implemented in backend/src/features/stats/)

- `GET /v1/stats` → the counts in root CLAUDE.md 5.14. Read-only, no login (it is the public screen's source).
- A phone is **counted** when it calls `GET /v1/sync` or `GET /v1/lookup` with `X-Linda-Device`, or sends a report
  (`device_id`). Only the ID and two times are kept (`phones_seen`: first and last check-in). An ID that is not 8–64
  lowercase hex characters, or that starts with `RADAR_DEMO_TRUSTED_PREFIX` (the simulator's phones), is ignored.
- `phones.active_24h`: last check-in within 24 hours. A phone syncs about every 15 minutes while it has a network.
- `scams_caught`: reports from real phones (`last_24h` by the gateway's clock). Only phones with reports switched on
  send reports, so this is a floor.
- `numbers`: of the numbers at least one real phone reported, how many are confirmed and suspected now. The fraud team's
  seeded numbers and numbers only the simulation reported do not count.
- `by_category`: reports from real phones per 5.1 category, biggest first.
- `recent`: the newest 8 reports from real phones: category, `at` (unix seconds), the number masked as `0700 ••• 777`
  (first four and last three digits of the local form; `•••` for anything else), and the number's status now.

