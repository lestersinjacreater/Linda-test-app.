# Mock fraud intelligence

Pretends to be Safaricom's fraud team for the demo (root CLAUDE.md 5.12).

```bash
pip install -r fraudintel/requirements.txt
cd fraudintel && uvicorn --factory src.main:create_app --port 8002
python -m pytest fraudintel/tests        # from the repo root
```

Point the gateway at it with `FRAUDINTEL_URL=http://localhost:8002`.

## What it knows at start (`data/seed.json`, fake numbers)

| Number | Status | Why |
|---|---|---|
| 254700000777 | blocked | the app's demo-mode scammer, so "Reported for fraud" works on a fresh install |
| 254700000801 | blocked | a "sent by mistake" scammer |
| 254700000802 | campaign | linked to the prize campaign |
| 254700000803, 254700000804 | reported | told to 333, not proven: suspected only |

Plus three campaigns (fake M-Pesa, sent by mistake, prize) with SimHash fingerprints, and the verified list from
`shared/verified_callers.json`.

## Demo controls

```bash
# "The fraud team just blocked this number" (the gateway picks it up within 30 seconds)
curl -X POST localhost:8002/admin/numbers -H 'content-type: application/json' \
  -d '{"msisdn": "254700000805", "status": "blocked", "category": "prize"}'
# What Linda has sent back to the fraud team (no message text, ever)
curl localhost:8002/v1/intel/reports
# Back to the seed
curl -X POST localhost:8002/admin/reset
```

In production deployment these sit behind the presenter's password at `https://DOMAIN/fraud/...`.
