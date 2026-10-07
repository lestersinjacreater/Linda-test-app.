# backend/: the radar (FastAPI + Postgres; SQLite allowed for local dev)

Replaces `radar_stub/` from the simulator starter. Implements root sections 5.1–5.3, 5.6, 5.7.

## Structure
```
backend/src/
  main.py
  core/        config.py (env), db.py, security.py (rate limits, integrity check)
  features/
    reports/       POST /v1/reports, storage
    confirmation/  evaluate_sender(), trust weights, allowlist, network notifier
    lookups/       GET /v1/numbers/{msisdn}/risk, GET /v1/blocklist
    ussd/          POST /ussd (Africa's Talking menu)
    payments/      POST /v1/payments/precheck, POST /v1/payments/stk (Daraja sandbox)
backend/tests/
```

## Confirmation logic (the heart of the project)
- One vote per `device_id` per sender within `WINDOW_S` (default 1800 s).
- Score = Σ trust_weight × confidence. New devices start at trust 0.3; trust rises after reports
  that end up confirmed, falls after reports on senders that never get confirmed.
- Confirm when `devices ≥ MIN_DEVICES (3)` and `score ≥ MIN_SCORE (2.4)`, sender not allowlisted.
- Fingerprint boost: a new sender whose fingerprint is within Hamming distance ≤ 6 of a confirmed
  campaign becomes `suspected` immediately (never confirmed on fingerprint alone).
- On confirm: call `POST {TELCO_URL}/network/confirm` (retry 3× with backoff), record `confirmed_at`.
- Rate limits: max 30 reports per device per hour; reject otherwise.
- `integrity_token`: in the hackathon, accept missing tokens but give such devices trust ≤ 0.3.
  Document Play Integrity verification as the production path.

## Tests that must exist
- 3 independent devices confirm; 1 device × 10 reports does not.
- Verified senders (shared/verified_senders.json) are never confirmed.
- Poisoning: 20 brand-new devices reporting an innocent number within 1 minute does not confirm it
  (low trust), and the attempt is logged.
- USSD menu flows; precheck blocks confirmed numbers and allows unknown ones.

## Daraja
Sandbox only. Credentials from env (`DARAJA_CONSUMER_KEY`, `DARAJA_CONSUMER_SECRET`,
`DARAJA_SHORTCODE`, `DARAJA_PASSKEY`, `DARAJA_CALLBACK_URL`). Never log secrets.
