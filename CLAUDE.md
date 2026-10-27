# Linda: system build specification (root)

You are building Linda for the Safaricom "Intelligence Unleashed" intern hackathon
(hack day 28–29 Oct 2026). Read this whole file before every task. Each component folder
also has its own CLAUDE.md with details; read it when working in that folder. Where a
component file disagrees with this one, this file wins.

## 1. What Linda is

**Linda** (Swahili for "protect") is an AI-powered app that brings Safaricom's fraud
intelligence onto the customer's phone, warning them about scams at the moment a scam
reaches them, and catching new scams nobody has reported yet.

The problem: Safaricom's fraud team already knows a lot about scammers (numbers reported to
333, blocked numbers, known campaigns, genuine official numbers), but that knowledge stays
behind the scenes. Customers usually find out a number was a scam after they have lost money.

What Linda does for the customer:

- **Scam SMS:** reads each incoming SMS on the device, scores it with an on-device model,
  and warns on the notification before the message is opened.
- **Scam callers:** labels calls from reported or confirmed scam numbers before they are answered.
- **Verified callers and businesses:** shows a verified badge for genuine numbers
  ("Verified: Safaricom Customer Care", 0722 000 000, banks, verified businesses), so impersonation fails.
- **One-tap reports:** customer reports flow back into Safaricom's fraud intelligence.

Two sources of intelligence work together:

1. **What Safaricom already knows,** delivered through the **Linda gateway** (`backend/`), which
   sits between phones and the fraud team's systems. In the hackathon those systems are
   simulated by `fraudintel/`; in production the gateway plugs into the real ones.
2. **What Safaricom does not know yet,** from Linda's own ML: the on-device model catches brand-new
   scam messages from numbers nobody has reported, and fingerprint matching links new numbers to
   known campaigns (scammers switching SIM cards).

**The phone network protects people without the app.** Every Linda phone is a sensor. When several
independent Linda phones catch the same scammer, the gateway confirms it, and Safaricom's network
(simulated by `simulator/`) warns everyone who received messages or calls from that number, on any
phone, including basic feature phones. With 1% of people running Linda, a scam blast to 500 people
reaches 3 or more Linda phones about 88% of the time.

**Positioning.** Linda complements Safaricom's fraud systems and Kaa Rada; it does not replace them.
Kaa Rada protects the transaction; Linda protects the conversation and the decision, earlier.

- **Pitch line:** "Safaricom already catches scammers behind the scenes. Linda brings that protection
  onto the customer's screen the moment a scam arrives, and catches the new scams no one has reported yet."
- **Network line:** "Every Linda user is a scam sensor. When a few catch a scam, Safaricom can warn
  everyone who received it, on any phone, within minutes."
- Never pitch Linda as "uncovering fraud networks". That is the fraud team's job (and Kaa Rada's).

For the hackathon Linda is a standalone Android app. In production it would live inside My OneApp.

## 2. Non-negotiables (never break these)

1. **No message text ever leaves a phone.** Reports contain only the fields in section 5.1.
2. **Demo infrastructure is separable.** `simulator/` (Safaricom's network) and `fraudintel/`
   (the fraud team's systems) are stand-ins. Nothing in `android/`, `backend/` or `ml/` may import
   from or depend on them. Components talk only through the HTTP/WebSocket contracts in section 5.
3. **The Android app works with no server at all.** Detection is fully on-device; network
   features degrade gracefully when the gateway is unreachable.
4. **Never wrongly flag a real M-Pesa, bank, KPLC or OTP message.** Verified senders
   (`shared/verified_senders.json`) are never reported or confirmed.
5. **Every warning is explained** in plain English and Swahili ("This pretends to be M-Pesa
   but came from a personal number").
6. **Never leak fraud intelligence to scammers.** Phones never download a full list of flagged
   numbers and never send a full number to look one up: they use hash-prefix lookups (5.10).
   Phones receive only a risk level, a scam type and a short customer-facing reason, never the
   fraud team's evidence. Lookups are rate-limited.
7. **Linda warns; the customer decides.** Never auto-block, auto-reject or delete anything.
   A verified badge is shown only for numbers on the verified list, never inferred.
8. **Lightweight by design** (section 7.1). Do not add a dependency or ML runtime that breaks the budgets.
9. **Honest numbers.** Only report test results on data the model never trained or tuned on, per
   source. Never present Tanzanian, international or synthetic results as results on Kenyan scams.
10. **Hackathon rules translated into code rules:**
    - All code is written inside the official hacking period (from 5 Oct 2026). No pre-existing
      code is copied in, except code produced in this repo during the period.
    - Every library goes in `LIBRARIES.md`, every dataset in `DATASETS.md`, with licence.
    - Safaricom APIs (Daraja) use the **sandbox only**. No live credentials anywhere.
    - Every part must be explainable by the team: when you finish a feature, add a short
      plain-language section to `docs/EXPLAINED.md`.
11. **Secrets** come from environment variables only (`.env`, git-ignored, with `.env.example`
    committed). Raw datasets, processed real data and real people's messages are never committed.

## 3. Repo layout

```
linda/
  CLAUDE.md                  this file
  android/                   Kotlin app (has its own CLAUDE.md: detection details)
  ml/                        dataset pipeline, training, evaluation, export
  backend/                   the Linda gateway (FastAPI): reports, confirmation, lookups,
                             verified callers, fraud-intel sync, USSD, precheck
  fraudintel/                mock Safaricom fraud intelligence service (demo infrastructure)
  simulator/                 mock telco (FastAPI): population, scam blasts, delivery log, warnings
  dashboard/                 Next.js dashboard: real numbers at /, the simulation at /simulation
  shared/                    contracts, test vectors, verified senders, model artifacts
  docs/                      EXPLAINED.md, technical-brief.md, demo-script.md
  docker-compose.yml         gateway + fraudintel + telco + dashboard + postgres
  Makefile                   one-word commands (see section 7)
  LIBRARIES.md  DATASETS.md  .env.example
```

Older code and docs call the gateway "the radar". They are the same service (`backend/`).

## 4. How the pieces fit

```
 incoming SMS / call ──> [android app] ──warn or verify on device (works offline)
                              │  hash-prefix lookups, reports (no text)
                              ▼
 [fraudintel: mock fraud team] <──sync / forward──> [backend: Linda gateway]
                                                        │ confirm ▲ "who received from X?"
                                                        ▼ │
                              [simulator: mock telco] ──warnings──> all recipients, any phone
                                     └──events (WebSocket)──> [dashboard]
 [backend] also serves: USSD checker, payment precheck (Daraja sandbox)
 [ml] ──exports model──> android assets + simulator models (same version)
```

## 5. Contracts (the only way components talk)

Change a contract only by updating `shared/contracts.md`, every side that uses it, and its tests,
in the same commit. Phone numbers are always `2547XXXXXXXX` (no plus) inside the system.
Sections marked **PLANNED** are the target design and are not built yet; when you build one,
remove the marker in the same commit.

### 5.1 Phone → gateway: `POST /v1/reports`
```json
{ "sender": "254712345678", "category": "sent_by_mistake", "confidence": 0.93,
  "fingerprint": "a3f09c1e7b2d4f80", "model_version": "2026.10.17-1",
  "device_id": "9c1f0a2b3d4e5f60", "integrity_token": "optional", "sent_at": "2026-10-20T14:02:11Z" }
```
- `fingerprint`: 64-bit SimHash (hex) of `text_normalized` computed on the device.
- `device_id`: random UUID created at install, hashed; never the phone number.
- Response: `{ "status": "unknown|suspected|confirmed|allowlisted", "devices": 3 }`

### 5.2 Server-side → gateway: `GET /v1/numbers/{msisdn}/risk`
`{ "msisdn": "...", "status": "unknown|suspected|confirmed|allowlisted|verified", "category": "...", "reports": 12, "confirmed_at": null }`
For a number on the verified-callers list, `status` is `verified` and the response adds `name_en` and `name_sw`.
Takes a full number, so it is for server-side callers only (USSD, simulator, precheck, tests). Phones use 5.10.

### 5.3 Phone → gateway: `GET /v1/blocklist?since=<iso>`
Confirmed senders changed since a time, as full numbers:
`{ "as_of": "...", "added": [{"msisdn": "...", "category": "..."}], "removed": ["..."] }`
Server-side and tests only. Phones use the hashed sync in 5.11 (principle 6).

### 5.4 Gateway → network (simulator today, Safaricom tomorrow)
- `POST {TELCO_URL}/network/confirm` `{ "sender": "..." }` → `{ "warned_now": 214 }`
- `GET  {TELCO_URL}/deliveries?sender=...&since=<sim seconds>` → `{ "recipients": [...] }`

### 5.5 Simulator → dashboard: WebSocket `{TELCO_URL}/events`
Event objects `{ "type": ..., "sim_time": ..., ...}` with types:
`reset, blast_started, sms_delivered, linda_detected, report_sent, report_failed,
sender_confirmed, warning_delivered (before_read: bool, channel: sms|after_call, real_sms: bool), blast_finished,
call_attempt (known: bool), call_labelled (label: reported|recent_scam_message|suspected)`.
`GET /metrics?sender=` returns the headline numbers (delivered, warned, warned_before_read_pct,
feature_phones_warned_before_read_pct, first_detection_after_s, confirmed_after_s, adoption_pct, linda_recipients,
calls, real_phone_sms). Scenarios take `?adoption=<percent>` (0.5, 1, 2: a 2,000-phone country, 500 recipients);
`POST /scenarios/call[?caller=2547...]`; `GET /adoption/odds` replays the blast many times per level (details in `shared/contracts.md`).

### 5.6 USSD (Africa's Talking callback): `POST /ussd` (form fields)
`sessionId, serviceCode, phoneNumber, networkCode, text` → plain-text reply starting with
`CON ` (menu continues) or `END ` (session ends). Menu: 1 Check a number, 2 How to stay safe, 3 Kiswahili
(the same two choices: `3*1`, `3*2`). Each dialling phone may check `LOOKUP_RATE_PER_HOUR` numbers an hour.

### 5.7 Payment precheck: `POST /v1/payments/precheck`
`{ "recipient": "2547...", "amount": 1500 }` → `{ "allow": false, "reason": "...", "reason_sw": "...", "status": "confirmed" }`
(only `confirmed` blocks; rate-limited like 5.10). If allowed, the demo payment triggers a Daraja **sandbox** STK Push via
`POST /v1/payments/stk` `{ "payer", "recipient", "amount" }`, which re-checks the recipient and only prompts phones in
`DARAJA_ALLOWED_PAYERS`; the sandbox reports back to `POST /v1/payments/stk/callback`.
Lowest priority: Linda's story is the conversation, not the payment.

### 5.8 Model artifact: `shared/models/model-<version>.json`
`{ "version", "created_at", "normalizer_version", "vectorizer": {"analyzer","ngram_range","hash": "fnv1a_64",
  "buckets": 32768, "idf_scale": 16, "idf_uint8_b64"}, "classes", "coef_scale", "coef_int8_b64", "intercept",
  "metadata_features", "thresholds": {"warn": 0.55, "scam": 0.80}, "categories": {...}, "metrics": {...} }`
Lightweight (7.1): letter pieces are hashed into 32,768 slots (FNV-1a 64, the same function as the fingerprint) instead
of a vocabulary; the idf and the weights are one byte per slot, stored as base64 inside this one JSON file (about 87 KB).
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
score ranges are regenerated by the model export.
Python (ml, simulator) and Kotlin (android) tests must all pass against the same file.
Minimum 60 cases, including verified M-Pesa messages (must be SAFE).

### 5.10 Phone → gateway: private lookup `GET /v1/lookup?prefix=<5 hex chars>`
The phone computes `h = sha256_hex(msisdn)` (lowercase hex of the UTF-8 `2547XXXXXXXX`; parity in
`shared/hash-vectors.json`) and sends only the first 5 characters, with header `X-Linda-Device: <device_id>`.
The gateway returns every known number whose hash starts with that prefix; the phone checks for its own `h`.
```json
{ "prefix": "a3f09", "entries": [
  { "hash": "a3f09c...", "status": "confirmed|suspected|reported|verified", "category": "sent_by_mistake",
    "label": "Reported for fraud by 14 people", "label_sw": "Imeripotiwa kwa ulaghai na watu 14" },
  { "hash": "a3f09e...", "status": "verified", "category": null,
    "label": "Safaricom Customer Care", "label_sw": "Huduma kwa Wateja ya Safaricom" } ] }
```
A number with no matching entry is `unknown`. Entries have exactly these five fields: labels are customer-facing,
never evidence. A prefix that is not exactly 5 hex characters is refused (422). Rate limit `LOOKUP_RATE_PER_HOUR`
(120) per device id (or caller address), then 429. The app uses it at call time when its offline knowledge finds
nothing, with a 1.5 s timeout: `confirmed` gives the full warning, `suspected` or `reported` a softer one.

### 5.11 Phone → gateway: hashed sync `GET /v1/sync?since=<iso>`
For offline call screening: `{ "as_of": "...", "added": [{"hash", "status": "confirmed", "category", "label", "label_sw"}],
"removed": ["<hash>"] }`. Only numbers confirmed in the last `SYNC_RECENT_DAYS` (30); `removed` holds numbers a human
allowlisted or that aged out, so the phone's list stays short. Suspected and reported numbers never appear here, and
neither do verified callers (they are public and come from 5.13). Never full numbers. Same rate limit as 5.10.
Honest limit: phone numbers are a small space, so a hash can be reversed by trying every number. The protection is
that the full list is never downloadable, the offline list is short and recent, and lookups are rate-limited.

### 5.12 Gateway ↔ fraud intelligence (`fraudintel/` today, Safaricom's systems tomorrow)
- `GET  {FRAUDINTEL_URL}/v1/intel/numbers?since=<iso>` → `{ "as_of", "numbers": [{"msisdn", "status":
  "reported|blocked|campaign", "category", "reports", "campaign_id"}] }`
- `GET  {FRAUDINTEL_URL}/v1/intel/verified` → `{ "numbers": [{"msisdn", "name_en", "name_sw", "kind": "safaricom|bank|business"}] }`
- `GET  {FRAUDINTEL_URL}/v1/intel/campaigns` → `{ "campaigns": [{"id", "category", "fingerprints": ["<simhash>"]}] }`
- `POST {FRAUDINTEL_URL}/v1/intel/reports` ← `{"kind": "report|confirmation", "sender", "category", "at",
  "confidence"?, "fingerprint"?, "model_version"?, "device_id"?}`. Never message text: the service rejects unknown fields.
The gateway pulls every `INTEL_SYNC_S` (30 s) when `FRAUDINTEL_URL` is set; with it empty or unreachable the gateway works
as before. Merge rules: "blocked" or "campaign" counts as confirmed (reaches the phone blocklist, and the network is told);
"reported" counts as suspected in risk lookups only (never on the phone blocklist); verified numbers are never confirmed;
campaign fingerprints make a new number suspected on its first matching report; the fraud team's verified list is merged
into 5.13. Demo-only extras on `fraudintel/` (not part of the contract): `GET /v1/intel/reports` (the fraud team's inbox),
`POST /admin/numbers` (flag a number live), `POST /admin/reset`.

### 5.13 Phone → gateway: `GET /v1/verified-callers`
`{ "version": "2026.10.19-1", "callers": [{ "msisdn": "254722000000", "name_en": "Safaricom Customer Care",
"name_sw": "Huduma kwa Wateja ya Safaricom", "kind": "safaricom|bank|business" }] }`
Source of truth: `shared/verified_callers.json` (each entry records its official `source`). The app bundles a copy in
`android/app/src/main/assets/verified_callers.json` (must equal the shared file; a unit test checks) and replaces it
with the gateway's copy after each sync. The list is public, so sending all of it is allowed (unlike flagged numbers).
A verified number is never confirmed as a scammer, and a scam signal on a number always beats its badge.

### 5.14 Dashboard → gateway: `GET /v1/stats`
Real counts for the dashboard's main page: `{ "as_of", "phones": {"total", "active_24h"}, "scams_caught": {"total",
"last_24h"}, "numbers": {"confirmed", "suspected"}, "by_category": {"<category>": n}, "recent": [{"category", "at",
"number": "0700 ••• 777", "status"}] }`. Counts only, never a full number (principle 6). Phones are counted by the
anonymous `X-Linda-Device` on 5.10 and 5.11 and the `device_id` of 5.1; simulated phones (`RADAR_DEMO_TRUSTED_PREFIX`)
and numbers no real phone reported are left out. Details in `shared/contracts.md`.

### 5.15 Fraud-team console → gateway: `/v1/fraud-team/*`
The screen a fraud analyst works from (dashboard page `/fraud-team`). `GET /v1/fraud-team/console` → `{ "as_of", "counts":
{"reports", "waiting", "confirmed", "phones"}, "queue": [{"msisdn", "number", "category", "reports", "phones", "first_at",
"last_at", "status": "reported|suspected|confirmed|allowlisted", "new"}], "patterns": {"<category>": n}, "feed": [{"at",
"kind": "report|confirmed", "number", "category"}] }`; `POST /v1/fraud-team/confirm {"msisdn"}` (a person confirms: phones
and the network are told, as for 3 phones; verified or unreported numbers → 409) and `POST /v1/fraud-team/clear {"msisdn"}`
(not a scam). FULL numbers, so: off unless `RADAR_FRAUD_TEAM_CONSOLE` is on; Caddy asks for the presenter password and
then adds `X-Fraud-Team-Key` (`FRAUD_TEAM_KEY`), which the gateway checks. Real phones only, as in 5.14.

## 6. Components (summary; details in each folder's CLAUDE.md)

| Folder | Owner pair | Builds into | Key acceptance |
|---|---|---|---|
| `ml/` | ML pair | `model-<version>.json` + `ml/reports/metrics.md` | Real test results per source; Kenyan campaign-split test once team data exists; false alarms on hard negatives near zero |
| `android/` | Android pair | APK via GitHub Actions release | Warns before opening; labels and verifies callers; never flags verified senders; works offline; parity with test vectors |
| `backend/` | Backend pair | Docker image | Confirms only with ≥3 independent devices; resists poisoning; private lookups; merges fraud intel; all endpoints in section 5 |
| `fraudintel/` | Backend pair | Docker image | Seeded reported, blocked and verified numbers and campaigns (placeholder numbers); endpoints in 5.12; rejects message text |
| `simulator/` | Demo owner | Docker image | Repeatable blast (fixed seed); adoption levels; warnings to phones without Linda; metrics endpoint; uses the real model |
| `dashboard/` | Demo owner | Docker image | Live map + timeline + metrics; demo mode with hardcoded fallback data if WS is down |

## 7. Build and run

- `make setup` install Python deps for ml/backend/simulator, npm deps for dashboard
- `make data` build the synthetic dataset and, if the raw files exist, the public dataset (`ml/data/README.md`)
- `make train` train + evaluate, writes `ml/reports/metrics.md`
- `make export-model` export and copy the model to android + simulator
- `make test` run all Python tests + shared test vectors (Kotlin tests run in CI)
- `make up` `docker compose up --build` (gateway, telco, dashboard, postgres)
- `make demo` reset the simulator and start the scripted scam blast
- Android: GitHub Actions builds the APK on the default branch → Releases. Installed phones update in place from
  Settings → Updates (`features/update/`, hackathon only; same signing key, higher build number, Android confirms).

CI: one workflow per folder with `paths:` filters, plus `shared-contracts.yml` that runs the test
vectors against Python and Kotlin whenever `shared/`, normalizer or model code changes.
Deployment for hack day: gateway + telco + dashboard on one small cloud server (primary), the same
`docker-compose` on a laptop (backup). Zero budget: the same server as one free Render service (`render.yaml`,
`deploy/render/`, docs/DEPLOY.md "Free on Render"); phones get its address from the GitHub variable `LINDA_SERVER_URL`. The app has a hidden settings screen to switch server URL.
The repo's default branch is `very/optimistic-shannon-rzw9xm`; open pull requests against it.

### 7.1 Lightweight budgets (measure and report them)

| Measure | Budget |
|---|---|
| APK download size | under 5 MB |
| Model file | under 300 KB (currently about 87 KB; a test checks it) |
| Memory while running | under about 30 MB |
| Scoring one SMS | under 20 ms on a budget phone |
| Battery | event-driven only: no permanent background service; sync a few times a day on network |
| Data | a few KB per day; lookups and sync send hashes and deltas only |

Call screening needs Android 10+; on older phones the app still protects SMS and skips call labels.

## 8. Phases, status and remaining work

**Done (P0–P2):** walking skeleton, on-device detection and warnings, consent and reports, call
screening and blocklist sync, radar confirmation with trust weights and poisoning resistance, simulator
scenarios (blast, rotating, poison, call) and metrics, dashboard, deployment config, CI per folder.
Model `2026.10.19-1` trained on synthetic + real public data (Bongoscam, Mendeley); replaced by the lightweight `2026.10.20-1` (same real-test results, 87 KB), then by `2026.10.21-1`, which adds a
public Kenyan SMS set re-labelled by Claude (82% of Kenyan test scams caught, up from 61%; see `ml/reports/metrics.md`), then by
`2026.10.25-1`, which adds a team member's donated real inbox (private, never committed; false alarms on that real inbox's
test part 20% -> 3.8% with all its test scams still caught; Kenyan public set 76.5% caught, false alarms 31.8% -> 21.2%). Now
`2026.10.26-1`, which adds a second donated inbox (private; on that inbox's test part 16 of 16 scams caught, false alarms
6.6% -> 5.0%; Kenyan public set 74.5% caught, false alarms 21.2% -> 9.1%; all real Kenyan test scams in both inboxes caught).

**Dates (extended):** building continues until the end of **27 Oct**, then FEATURE FREEZE. Hack day is 28–29 Oct.

**Done since 22 Oct:** the virtual phone tour (`tools/virtual-phone/tour.py`, CI on every app PR; a browser-usable
"live phone" was tried and removed on 25 Oct, see `docs/EXPLAINED.md`); on-device learning in `android/.../features/learning/` (never leaves the phone): **M-Pesa memory** (checks "I sent you
money by mistake" claims against the person's real M-Pesa messages), **know your senders** (softens wording-only
warnings from numbers the person really talks to; warns on the "new number" trick and strangers asking for money),
**learn from your taps** (a capped personal layer trained by "Mark as safe" and "Report").

**Remaining, in priority order (until the freeze at the end of 27 Oct):**
1. Kenyan data: public Kenyan set (`kenya_sms`) and two donated team inboxes (`team_collected`, model `2026.10.26-1`) added.
   Still to do: team spot-check of the public-set labels.
2. ~~`fraudintel/` mock service (5.12) and the gateway merging it.~~ Done.
3. ~~Verified-caller badges in the gateway and the app.~~ Done (5.13). In production the list comes from 5.12.
4. ~~Private hash-prefix lookups and hashed sync (5.10, 5.11), then retire 5.3 for phones.~~ Done.
5. ~~Lightweight model (5.8, 7.1 budgets), Python and Kotlin together.~~ Done (model `2026.10.20-1`, 87 KB).
6. ~~Network effect in the simulator: adoption levels (0.5%, 1%, 2%), warnings after scam calls, warning
   SMS to a real basic phone via Africa's Talking (if organisers allow).~~ Done. A real SIM needs a live Africa's
   Talking account (the sandbox only reaches its web simulator) and the organisers' OK.
7. ~~USSD checker (5.6). Stretch.~~ Done (`backend/src/features/ussd/`; Africa's Talking sandbox callback `/radar/ussd`).
8. ~~Payment precheck (5.7). Drop if short of time.~~ Done in the gateway (`backend/src/features/payments/`); no app screen.
9. ~~Dashboard updates for fraud-intel hits, verified callers and adoption levels.~~ Done: adoption, call and
   fraud-team buttons, run notes, KNOWN numbers. Verified callers show on the phone (the simulator has no verified calls).

**P3 Polish (alongside the last build days, and after the freeze).** Bug fixes only after 27 Oct. Technical brief
(2 pages), pitch deck (8 slides), recorded backup demo video, three full rehearsals, one real-phone check of the APK.

## 9. How to work in this repo (instructions for Claude Code)

1. Work on **one item and one component at a time**. Before writing code, state your plan
   (files to create or change, tests to add) and wait for approval.
2. Read the component's CLAUDE.md before touching that folder.
3. Keep code **simple and explainable**: plain functions, clear names, comments that say *why*.
   The team must be able to explain every line to judges.
4. **Tests first for contracts and core logic** (normalizer, confirmation, lookups, precheck, metrics).
   Run `make test` before every commit; never commit red tests.
5. Small commits with clear messages (`backend: confirmation with trust weights`), on a branch, merged
   through a pull request against the default branch. Never push straight to the default branch.
6. Never add a dependency without adding it to `LIBRARIES.md` (name, version, licence, why).
7. Never change a contract in section 5 silently (see section 5 rule).
8. Never commit secrets, raw data, processed real data, or real people's messages.
9. If something is ambiguous, **ask**; do not guess on contracts, privacy or rules.
10. After each feature, update `docs/EXPLAINED.md` (what it does, how, why this way).
11. For UI, motion and typography work, `docs/design-system.md` is the source of truth. The third-party `apple-design` skill
    (`.claude/skills/apple-design/`, MIT, guidance only, written for the web) may inform how things move (springs, interruptible
    animation, reduced motion, tracking and leading), translated to Compose for the Android app. Where it conflicts with
    `docs/design-system.md`, the design system wins. Never copy code from it.

## 10. Demo flow the system must support (see docs/demo-script.md)

1. Quiet network on the dashboard map.
2. A scam SMS reaches a Linda phone: warned on the notification before it is opened, with the reason.
3. A call from a reported number is labelled "Reported for fraud" before answering; a call from
   0722 000 000 shows "Verified: Safaricom Customer Care".
4. A brand-new number nobody has reported sends a scam: Linda's model catches it on the device.
5. Scam blast: Linda phones detect it; reports flow with no message text; the gateway confirms.
6. A wave of warnings reaches phones **without** Linda, including feature phones; a real basic phone
   on the table receives the warning.
7. Rerun at 0.5%, 1% and 2% adoption: protection improves as adoption grows.
8. A judge reports a number in one tap and sees it flow back to the (mock) fraud team.
9. Metrics: time to detection, time to confirmation, % warned before reading.
10. Optional: USSD check on a basic phone; payment precheck before money moves.

## 11. Out of scope

Play Store publishing, iOS, live Safaricom integration (production would plug the gateway into the real
fraud systems and network, and ship Linda inside My OneApp), SIM toolkit applets, reading or recording
call audio, network-side content scanning, network-level fraud detection (the fraud team's and Kaa Rada's
job), blocking, rejecting or deleting messages or calls (Linda warns; the user decides).
