#!/usr/bin/env bash
# The live phone session. Runs inside the emulator step of .github/workflows/live-phone.yml, with the emulator booted.
#   1. If LIVE_PHONE_KEY is set: start the browser screen (ws-scrcpy), the key gate in front of it, and a free
#      temporary web address (cloudflared quick tunnel) pointing at the gate.
#   2. Optionally let a headless Chrome on the runner open that address and screenshot it (CHECK_SCREEN=true).
#   3. Run the command listener until the session time is up or someone sends /phone stop.
set -u
APK=android/app/build/outputs/apk/debug/app-debug.apk
OUT=live-phone-out
mkdir -p "$OUT"
URL=""
SCREEN_URL=""  # the address announced on the issue (only when the owner's key is set)

if [ -z "${LIVE_PHONE_KEY:-}" ] && [ "${CHECK_SCREEN:-false}" = "true" ]; then
  # No key yet, but a self-check was asked for: use a throwaway key that is hidden in the logs and never posted,
  # so the screen can be tested. Nobody can open it, and the issue says the screen is off.
  LIVE_PHONE_KEY=$(openssl rand -hex 16)
  echo "::add-mask::$LIVE_PHONE_KEY"
  export LIVE_PHONE_KEY
  THROWAWAY=1
fi

if [ -n "${LIVE_PHONE_KEY:-}" ]; then
  # ws-scrcpy serves the page and its live connection under /<key>/ (on 127.0.0.1:8000).
  (cd ws-scrcpy/dist && WS_SCRCPY_PATHNAME="/$LIVE_PHONE_KEY/" node index.js > "../../$OUT/ws-scrcpy.log" 2>&1 &)
  # The gate is the only thing the tunnel reaches: no key in the path, no connection.
  python3 tools/virtual-phone/gate.py --listen 8080 --to 8000 > "$OUT/gate.log" 2>&1 &
  ./cloudflared tunnel --no-autoupdate --url http://127.0.0.1:8080 > "$OUT/cloudflared.log" 2>&1 &
  for _ in $(seq 1 60); do
    URL=$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "$OUT/cloudflared.log" | head -1)
    [ -n "$URL" ] && break
    sleep 2
  done
  echo "screen address: ${URL:-none}"
  [ -z "${THROWAWAY:-}" ] && SCREEN_URL="$URL"
  if [ -n "$URL" ] && [ "${CHECK_SCREEN:-false}" = "true" ]; then
    sleep 10  # let the new address reach Cloudflare's edge
    timeout 180 python3 tools/virtual-phone/screen_check.py --url "$URL" --out "$OUT" \
      || echo "screen check failed" >> "$OUT/screen-check.txt"
  fi
else
  echo "LIVE_PHONE_KEY is not set: no browser screen this session (commands still work)."
fi

python3 tools/virtual-phone/listen.py --apk "$APK" --minutes "${MINUTES:-60}" --screen-url "$SCREEN_URL" --out "$OUT"
