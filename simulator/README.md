# simulator: mock telco (demo infrastructure, NOT part of the product)

Stands in for Safaricom's network. It only talks to the radar through the contracts in the root `CLAUDE.md`
(5.1 reports, 5.4 confirm and deliveries, 5.5 events). Nothing in `android/`, `backend/` or `ml/` imports it.

    uvicorn --factory src.main:create_app --port 8000       (env: RADAR_URL, SIM_SPEED, SIM_SEED, SIM_DEVICE_PREFIX)

| Call | What it does |
|---|---|
| `POST /scenarios/blast[?wait=true]` | Fake M-Pesa scam sent to 240 of 300 phones. Linda phones detect it with the real model and report; the radar confirms; the network warns everyone else. |
| `POST /scenarios/rotating` | The same script from three numbers in turn. |
| `POST /scenarios/poison` | 20 brand-new fake devices report an innocent number; the radar must not confirm it. |
| `POST /reset`, `GET /metrics?sender=`, `WS /events` | Presenter reset, headline numbers, live event stream. |
| `POST /network/confirm`, `GET /deliveries` | Called by the radar (contract 5.4). |

`SIM_SPEED` is simulated seconds per real second (default 20); `0` runs instantly (tests). Same `SIM_SEED`, same demo.
`src/vendor/linda/` is a GENERATED copy of the scoring code from `ml/` (`make export-model` refreshes it).
