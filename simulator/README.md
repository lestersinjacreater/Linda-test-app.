# simulator: mock telco (demo infrastructure, NOT part of the product)

Stands in for Safaricom's network. It only talks to the radar through the contracts in the root `CLAUDE.md`
(5.1 reports, 5.4 confirm and deliveries, 5.5 events). Nothing in `android/`, `backend/` or `ml/` imports it.

    uvicorn --factory src.main:create_app --port 8000       (env: RADAR_URL, SIM_SPEED, SIM_SEED, SIM_DEVICE_PREFIX)

| Call | What it does |
|---|---|
| `POST /scenarios/blast[?wait=true]` | Fake M-Pesa scam sent to 240 of 300 phones. Linda phones detect it with the real model and report; the radar confirms; the network warns everyone else. |
| `POST /scenarios/rotating` | The same script from three numbers in turn. |
| `POST /scenarios/poison` | 20 brand-new fake devices report an innocent number; the radar must not confirm it. |
| `?adoption=0.5` (or `1`, `2`) on blast, rotating, call | A 2,000-phone country where only that percent of PEOPLE run Linda; 500 recipients. Same phones and recipients at every level. |
| `POST /scenarios/call[?caller=2547...]` | The SMS blast, then the scammer phones people. Linda phones label the call before answering; once the number is confirmed, other phones get a warning SMS right after the call. With `caller`, calls only, and the telco asks the gateway (5.2) first. |
| `GET /adoption/odds?levels=0.5,1,2` | The same blast replayed 1,000 times per level: how often it is confirmed and how many are warned before reading. |
| `POST /reset`, `GET /metrics?sender=`, `WS /events` | Presenter reset, headline numbers, live event stream. |
| `POST /network/confirm`, `GET /deliveries` | Called by the radar (contract 5.4). |

`SIM_SPEED` is simulated seconds per real second (default 20); `0` runs instantly (tests). Same `SIM_SEED`, same demo.
`make demo-adoption` plays 0.5%, 1% and 2% and prints the odds.

**Real basic phone.** Put the team's phone in `REAL_PHONES` (`.env` only) and an Africa's Talking key in `AT_API_KEY`. That phone
joins every blast as a basic phone in Nairobi; when the network warns it, a real SMS starting "[Linda demo]" is sent (only the
warning, never the scam). With `AT_USERNAME=sandbox` it reaches AT's web simulator only; a real SIM needs a live account with
credit and the organisers' OK. No key: the status is `skipped` and the demo runs normally.
`src/vendor/linda/` is a GENERATED copy of the scoring code from `ml/` (`make export-model` refreshes it).
