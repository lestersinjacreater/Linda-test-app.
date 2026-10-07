# Linda: system build specification (root)

You are building Linda for the Safaricom "Intelligence Unleashed" intern hackathon
(hack day 28–29 Oct 2026). Read this whole file before every task. Each component folder
also has its own CLAUDE.md with details; read it when working in that folder.

## 1. What Linda is

Linda spots M-Pesa scams the moment they arrive, before the person knows someone is
trying to scam them, and protects people on every kind of phone.

- **On smartphones:** the Android app reads each incoming SMS on the device, scores it with
  an on-device model, and warns on the notification before the message is opened. It also
  labels calls from confirmed scam numbers before they are answered.
- **Across the network:** Linda phones send privacy-safe reports (never message text) to the
  **radar**. When several independent devices flag the same sender, the radar confirms it, and
  the network warns everyone who received messages from that number, including feature phones.
- **Pitch line:** "Kaa Rada stops the payment. Linda stops the conversation."

## 2. Non-negotiables (never break these)

1. **No message text ever leaves a phone.** Reports contain only the fields in section 5.1.
2. **The simulator is separable.** `simulator/` stands in for Safaricom's network and is demo
   infrastructure. Nothing in `android/`, `backend/` or `ml/` may import from or depend on it.
   Components talk only through the HTTP/WebSocket contracts in section 5.
3. **The Android app works with no server at all.** Detection is fully on-device; network
   features degrade gracefully when the radar is unreachable.
4. **Never wrongly flag a real M-Pesa, bank, KPLC or OTP message.** Verified senders
   (`shared/verified_senders.json`) are never reported or confirmed.
5. **Every warning is explained** in plain English and Swahili ("This pretends to be M-Pesa
   but came from a personal number").
6. **Hackathon rules translated into code rules:**
   - All code is written inside the official hacking period (from 5 Oct 2026). No pre-existing
     code is copied in, except code produced in this repo during the period.
   - Every library goes in `LIBRARIES.md`, every dataset in `DATASETS.md`, with licence.
   - Safaricom APIs (Daraja) use the **sandbox only**. No live credentials anywhere.
   - Every part must be explainable by the team: when you finish a feature, add a short
     plain-language section to `docs/EXPLAINED.md`.
7. **Secrets** come from environment variables only (`.env`, git-ignored, with `.env.example`
   committed). Raw datasets are never committed.

## 3. Repo layout

```
linda/
  CLAUDE.md                  this file
  android/                   Kotlin app (has its own CLAUDE.md: detection details)
  ml/                        dataset pipeline, training, evaluation, export
  backend/                   radar service (FastAPI): reports, confirmation, lookups, USSD, precheck
  simulator/                 mock telco (FastAPI): population, scam blasts, delivery log, warnings
  dashboard/                 Next.js live demo dashboard
  shared/                    contracts, test vectors, verified senders, model artifacts
  docs/                      EXPLAINED.md, technical-brief.md, demo-script.md
  docker-compose.yml         radar + telco + dashboard (+ postgres)
  Makefile                   one-word commands (see section 7)
  LIBRARIES.md  DATASETS.md  .env.example
```

## 4. How the pieces fit

```
 incoming SMS ──> [android app] ──warn user (on device)
                       │ report (no text)
                       ▼
 [simulator: mock telco] ──report──> [backend: radar] ──confirm──> [simulator] ──warnings──> all recipients
        │  delivery log  <──"who received from X?"──┘     │
        └──events (WebSocket)──> [dashboard]              ├─ risk lookups: USSD checker, call screening sync,
                                                          └─ M-Pesa precheck before Daraja STK Push
 [ml] ──exports model.json──> android assets + simulator models (same version)
```

## 5. Contracts (the only way components talk)

Change a contract only by updating `shared/contracts.md`, every side that uses it, and its tests,
in the same commit. Phone numbers are always `2547XXXXXXXX` (no plus) inside the system.

### 5.1 Phone → radar: `POST /v1/reports`
```json
{ "sender": "254712345678", "category": "sent_by_mistake", "confidence": 0.93,
  "fingerprint": "a3f09c1e7b2d4f80", "model_version": "2026.10.17-1",
  "device_id": "9c1f0a2b3d4e5f60", "integrity_token": "optional", "sent_at": "2026-10-20T14:02:11Z" }
```
- `fingerprint`: 64-bit SimHash (hex) of `text_normalized` computed on the device.
- `device_id`: random UUID created at install, hashed; never the phone number.
- Response: `{ "status": "unknown|suspected|confirmed|allowlisted", "devices": 3 }`

### 5.2 Anyone → radar: `GET /v1/numbers/{msisdn}/risk`
`{ "msisdn": "...", "status": "unknown|suspected|confirmed|allowlisted", "category": "...", "reports": 12, "confirmed_at": null }`

### 5.3 Phone → radar: `GET /v1/blocklist?since=<iso>`
Confirmed senders changed since a time, for offline call screening:
`{ "as_of": "...", "added": [{"msisdn": "...", "category": "..."}], "removed": ["..."] }`

### 5.4 Radar → network (simulator today, Safaricom tomorrow)
- `POST {TELCO_URL}/network/confirm` `{ "sender": "..." }` → `{ "warned_now": 214 }`
- `GET  {TELCO_URL}/deliveries?sender=...&since=<sim seconds>` → `{ "recipients": [...] }`

### 5.5 Simulator → dashboard: WebSocket `{TELCO_URL}/events`
Event objects `{ "type": ..., "sim_time": ..., ...}` with types:
`reset, blast_started, sms_delivered, linda_detected, report_sent, report_failed,
sender_confirmed, warning_delivered (before_read: bool), blast_finished`.
`GET /metrics?sender=` returns the headline numbers (delivered, warned, warned_before_read_pct,
feature_phones_warned_before_read_pct, first_detection_after_s, confirmed_after_s).

### 5.6 USSD (Africa's Talking callback): `POST /ussd` (form fields)
`sessionId, serviceCode, phoneNumber, networkCode, text` → plain-text reply starting with
`CON ` (menu continues) or `END ` (session ends). Menu: 1 Check a number, 2 How to stay safe.

### 5.7 Payment precheck: `POST /v1/payments/precheck`
`{ "recipient": "2547...", "amount": 1500 }` → `{ "allow": false, "reason": "...", "status": "confirmed" }`
If allowed, the demo payment screen triggers a Daraja **sandbox** STK Push via `POST /v1/payments/stk`.

### 5.8 Model artifact: `shared/models/model-<version>.json`
`{ "version", "created_at", "normalizer_version", "vectorizer": {"analyzer","ngram_range","vocabulary","idf"},
  "classes", "coef", "intercept", "metadata_features", "thresholds": {"warn": 0.55, "scam": 0.80},
  "categories": {...}, "metrics": {...} }`
`metadata_features` is an ordered list of `{name, coef}`; `verified_senders` is embedded; `training` records the data and seed.
Exact scoring maths: `shared/contracts.md`.
`make export-model` copies it to `android/app/src/main/assets/model.json` and
`simulator/models/model.json`. Both must report the same `version`.

### 5.9 Shared test vectors: `shared/test-vectors.json`
Each case: `{ "id", "sender", "text", "expected_normalized", "expected_label", "expected_score_min",
"expected_score_max", "expected_fingerprint" }`, plus `campaign`, `language`, `is_hard_negative`, `tags`,
optional `must_include_reason` and `sender_in_contacts`. `expected_label` is `SAFE`, `CAUTION` or `SCAM`.
Personal senders are `2547XXXXXXXX` placeholders (254700000xxx); company senders are IDs like `MPESA`.
`expected_normalized` and `expected_fingerprint` are generated by `ml/src/features/vectors/fill_derived.py`;
score ranges are provisional (by label) until the model export regenerates them.
Python (ml, simulator) and Kotlin (android) tests must all pass against the same file.
Minimum 60 cases, including verified M-Pesa messages (must be SAFE).

## 6. Components (summary; details in each folder's CLAUDE.md)

| Folder | Owner pair | Builds into | Key acceptance |
|---|---|---|---|
| `ml/` | ML pair | `model-<version>.json` + `ml/reports/metrics.md` | Campaign-split test on real Kenyan data; precision/recall/false alarms on hard negatives reported |
| `android/` | Android pair | APK via GitHub Actions release | Warns before opening; never flags verified senders; works offline; parity with test vectors |
| `backend/` | Backend pair | Docker image | Confirms only with ≥3 independent devices; resists poisoning test; all endpoints in section 5 |
| `simulator/` | Demo owner | Docker image | Repeatable blast (fixed seed); metrics endpoint; uses the real model |
| `dashboard/` | Demo owner | Docker image | Live map + timeline + metrics; demo mode with hardcoded fallback data if WS is down |

## 7. Build and run

- `make setup` install Python deps for ml/backend/simulator, npm deps for dashboard
- `make data` build the standardized dataset (`ml/`)
- `make train` train + evaluate, writes `ml/reports/metrics.md`
- `make export-model` export and copy the model to android + simulator
- `make test` run all Python tests + shared test vectors (Kotlin tests run in CI)
- `make up` `docker compose up --build` (radar, telco, dashboard, postgres)
- `make demo` reset the simulator and start the scripted scam blast
- Android: push to `main` → GitHub Actions builds the APK → Releases.

CI: one workflow per folder with `paths:` filters, plus `shared-contracts.yml` that runs the test
vectors against Python and Kotlin whenever `shared/`, normalizer or model code changes.
Deployment for hack day: radar + telco + dashboard on one small cloud server (primary), the same
`docker-compose` on a laptop (backup). The app has a hidden settings screen to switch server URL.

## 8. Phases and dates (definition of done for each)

**P0 Walking skeleton (by 12 Oct).** Repo structure, Makefile, docker-compose, CI per folder.
Baseline model trained on whatever data exists and exported. App detects + warns + sends a report.
Radar confirms after 3 devices and calls the simulator. Simulator runs a blast and warns.
Dashboard shows raw events. Everything connected end to end, even if ugly.

**P1 Real features (by 17 Oct).** Real dataset and model v1 with metrics. Call screening + blocklist
sync. USSD checker. Payment precheck + Daraja sandbox STK Push. Trust weights, allowlist, rate limits.
Dashboard map, timeline, metrics. Africa's Talking SMS warnings to a real phone (if organisers allow).

**P2 Hardening (by 22 Oct: FEATURE FREEZE).** Poisoning attack simulation + test. Threshold tuning
using the simulator. Demo mode everywhere. Kotlin/Python parity green. Docs: EXPLAINED.md complete.

**P3 Polish (23–27 Oct).** Bug fixes only. Technical brief (2 pages), pitch deck (8 slides),
recorded backup demo video, three full rehearsals. No new features after the freeze.

## 9. How to work in this repo (instructions for Claude Code)

1. Work on **one phase and one component at a time**. Before writing code, state your plan
   (files to create or change, tests to add) and wait for approval.
2. Read the component's CLAUDE.md before touching that folder.
3. Keep code **simple and explainable**: plain functions, clear names, comments that say *why*.
   The team must be able to explain every line to judges.
4. **Tests first for contracts and core logic** (normalizer, confirmation, precheck, metrics).
   Run `make test` before every commit; never commit red tests.
5. Small commits with clear messages (`backend: confirmation with trust weights`).
6. Never add a dependency without adding it to `LIBRARIES.md` (name, version, licence, why).
7. Never change a contract in section 5 silently (see section 5 rule).
8. Never commit secrets, raw data, or real people's messages.
9. If something is ambiguous, **ask**; do not guess on contracts, privacy or rules.
10. After each feature, update `docs/EXPLAINED.md` (what it does, how, why this way).

## 10. Demo flow the system must support (see docs/demo-script.md)

1. Quiet network on the dashboard map. 2. Scam blast starts. 3. Linda smartphones detect it;
reports flow with no message text. 4. Radar confirms; wave of warnings reaches feature phones.
5. A real basic phone receives the warning. 6. A judge forwards/receives a scam on a Linda phone
and sees the warning live. 7. A call from the scam number is labelled before answering.
8. Payment precheck blocks sending money to the scam number. 9. Metrics: time to detection,
time to confirmation, % warned before reading.

## 11. Out of scope

Play Store publishing, iOS, live Safaricom integration, SIM toolkit applets, reading call
audio, network-side content scanning, blocking or deleting user messages (Linda warns; the user decides).
