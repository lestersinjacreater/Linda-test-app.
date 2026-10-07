# Android: system integration additions

Keep the existing `android/CLAUDE.md` (detection spec). Append this file's sections to it,
or keep this file alongside it. The root CLAUDE.md overrides both on any conflict.

## New features (package-by-feature)

- `features/reporting` — After a SCAM verdict with score ≥ `thresholds.scam`, and only if the
  user opted in on the consent screen, send `POST /v1/reports` (root section 5.1). Queue reports
  in a local Room table and retry with backoff when offline. Never include message text.
  Compute `fingerprint` with the shared SimHash implementation (parity with test vectors).
- `features/consent` — First-run screen explaining in English and Swahili exactly what is sent
  (sender number, scam type, confidence, anonymous device ID) and what is never sent (message
  text, contacts). Default: OFF until the user taps Agree. Can be changed in settings.
- `features/callscreen` — `CallScreeningService` (request `RoleManager.ROLE_CALL_SCREENING`).
  On an incoming call, look up the local blocklist; if confirmed, post a high-priority
  notification "⚠️ Likely scam caller (M-Pesa fraud reported by N people)". Never auto-reject.
- `features/sync` — WorkManager job every 15 min (and on app open) calling `GET /v1/blocklist?since=`.
  Store in Room. Works offline from the last sync.
- `features/settings` — Hidden developer screen (tap version 7 times): server URL (cloud / laptop),
  demo mode toggle, model version display, last sync time.
- `features/demo` — Demo mode replays scripted scam messages and calls locally with no network.

## Acceptance

- Real M-Pesa confirmations from sender `MPESA` are never warned on and never reported.
- With the radar offline, detection and warnings still work; reports are queued, then delivered.
- Kotlin normalizer, scorer and SimHash pass every case in `shared/test-vectors.json` (CI).
- Model loaded from `assets/model.json`; its `version` is shown in settings.
