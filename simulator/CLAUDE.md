# simulator/: mock telco (demo infrastructure, NOT part of the product)

Starter code already exists (`src/core`, `src/features`, README). Extend it; do not rewrite it.
It stands in for Safaricom's network and must stay replaceable: it only talks to the radar through
root contracts 5.1, 5.4 and 5.5.

## To do
- Replace `features/detection.py` rules with the real model: load `models/model.json`, use the same
  normalizer, scorer and SimHash as ml/ (vendored copy so the Docker image is self-contained;
  parity checked by test vectors).
- Send reports with the full 5.1 schema (fingerprint, model_version, device_id, sent_at).
- Scenarios: `POST /scenarios/blast` (exists), `POST /scenarios/rotating` (same script from 3
  numbers in sequence), `POST /scenarios/poison` (fake devices report an innocent number),
  `POST /scenarios/call` (emits `call_attempt` events; warns feature phones of confirmed callers).
- Keep fixed seed + `SIM_SPEED` so the demo is identical every run.
- The metrics endpoint stays the source of the headline numbers.
