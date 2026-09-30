# Architecture

Feeds the 2-page technical brief. Status column shows what exists today.

```
            Incoming SMS / pasted text / shared text
                            │
                            ▼
                 ┌──────────────────────┐
                 │  Text normaliser     │  undo M-P3SA, K1m@kosa, spacing tricks
                 └──────────┬───────────┘
            ┌───────────────┼────────────────┐
            ▼               ▼                ▼
     ┌────────────┐  ┌────────────┐   ┌──────────────────┐
     │ Rules      │  │ ML model   │   │ Context signals  │
     │ (hard      │  │ (char      │   │ sender type,     │
     │ signals)   │  │ n-gram LR) │   │ in contacts?,    │
     └─────┬──────┘  └─────┬──────┘   │ first time?,     │
           │               │          │ links, timing    │
           │               │          └────────┬─────────┘
           └───────────────┼───────────────────┘
                           ▼
                 ┌──────────────────────┐
                 │  Fusion scorer       │  → Verdict(level, score, reasons)
                 └──────────┬───────────┘
                            ▼
           SAFE (silent) · CAUTION (quiet banner) · SCAM (loud warning)
                            │
         ┌──────────────────┼────────────────────┐
         ▼                  ▼                    ▼
   Notification +     History (Room DB)    Guardian alert,
   voice warning                           call warning
```

## Design rules
- One entry point: everything calls `ScamDetector.analyse(MessageInput): Verdict`. Nothing else scores messages.
- Everything runs on the phone. No network is needed for detection.
- Every verdict carries human-readable reasons in English and Swahili.
- Features depend only on `core/` and the detection API, never on each other.

## Build status

| Component | Status |
|---|---|
| App skeleton, theme, navigation (Home, Check, History, Settings) | Done (Phase 0) |
| Room database (`detections` table) | Done (Phase 0) |
| CI: build and publish APK, run ML tests | Done (Phase 0), see the Actions tab for the latest run |
| Shared test vectors (47) | Done (Phase 0); `expected_normalised` pending F1 |
| Normaliser, rules, ML model, fusion (F1-F4) | Not started |
| SMS receiver, alerts, checker, inbox scan, history (F5-F9) | Not started |
| Recovery, Guardian, voice, caller warning, demo mode (F10-F14) | Not started |
| Reporting, clustering, dashboard (F15-F17) | Not started |
