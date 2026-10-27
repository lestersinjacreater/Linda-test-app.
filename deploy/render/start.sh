#!/usr/bin/env bash
# Starts the five parts of Linda in one box (deploy/render/Dockerfile) and keeps them honest:
# if any one of them stops, the box stops too, and Render starts it again. A half-working server is worse than a
# restart, because it looks alive while the demo fails.
set -euo pipefail

APP="${LINDA_APP_DIR:-/app}"
PORT="${PORT:-10000}"                     # Render tells us which port to listen on

# Caddy listens on Render's port with plain http: Render's own edge already gives https to the world.
export SITE_ADDRESS=":${PORT}"
export TELCO_UPSTREAM=127.0.0.1:8000 RADAR_UPSTREAM=127.0.0.1:8001 FRAUD_UPSTREAM=127.0.0.1:8002 DASHBOARD_UPSTREAM=127.0.0.1:3000

# The presenter types a password in Render's dashboard; Caddy needs its bcrypt hash. Made here, never stored.
export PRESENTER_USER="${PRESENTER_USER:-presenter}"
if [ -z "${PRESENTER_PASSWORD:-}" ]; then
  echo "PRESENTER_PASSWORD is not set: refusing to start without a presenter password" >&2
  exit 1
fi
PRESENTER_HASH="$(caddy hash-password --plaintext "$PRESENTER_PASSWORD")"
export PRESENTER_HASH
# The fraud-team console (contract 5.15): on, with a fresh random key each boot that only Caddy and the gateway know.
export RADAR_FRAUD_TEAM_CONSOLE=1
FRAUD_TEAM_KEY="$(python -c 'import secrets; print(secrets.token_hex(24))')"
export FRAUD_TEAM_KEY

# How the parts find each other inside the box (the VPS uses docker service names instead).
export TELCO_URL=http://127.0.0.1:8000 FRAUDINTEL_URL=http://127.0.0.1:8002 RADAR_URL=http://127.0.0.1:8001
# The same demo settings as docker-compose.prod.yml.
export INTEL_SYNC_S="${INTEL_SYNC_S:-15}"
export RADAR_DEMO_TRUSTED_PREFIX="${RADAR_DEMO_TRUSTED_PREFIX-5111}" RADAR_DEMO_TRUSTED_TRUST="${RADAR_DEMO_TRUSTED_TRUST:-0.85}"
export SIM_DEVICE_PREFIX="${SIM_DEVICE_PREFIX:-5111}" SIM_SPEED="${SIM_SPEED:-20}" SIM_SEED="${SIM_SEED:-42}"
# Render's database address may start with postgres://; SQLAlchemy wants postgresql://. Empty = SQLite in the box.
case "${DATABASE_URL:-}" in
  postgres://*) export DATABASE_URL="postgresql://${DATABASE_URL#postgres://}" ;;
esac

start() {  # start <folder> <port>: one Python service, only reachable from inside the box
  (cd "$APP/$1" && exec python -m uvicorn --factory src.main:create_app --host 127.0.0.1 --port "$2") &
}
start fraudintel 8002
start backend 8001
start simulator 8000
(cd "$APP/dashboard" && PORT=3000 HOSTNAME=127.0.0.1 exec node server.js) &
caddy run --config "$APP/deploy/Caddyfile" --adapter caddyfile &

wait -n || true   # the first part to stop (whatever its exit code)
echo "A Linda service stopped; exiting so Render restarts the whole box" >&2
exit 1
