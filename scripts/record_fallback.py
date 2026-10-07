"""Records a real demo run as the dashboard's offline fallback (dashboard/src/lib/fallback/demo-events.json).

    python scripts/record_fallback.py      (or: make record-fallback)

Starts the real radar and simulator as separate processes (like tests/e2e), plays the blast at full
speed, and saves the phones, every event and the final metrics. The dashboard replays this file when
the live connection is down, so the stage never shows a blank screen. Re-run after changing the model
or the simulator so the replay matches what the real system does.
"""
import json
import os
import socket
import subprocess
import sys
import time
from pathlib import Path

import httpx

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "dashboard" / "src" / "lib" / "fallback" / "demo-events.json"


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def wait_healthy(url: str, seconds: float = 30.0) -> None:
    deadline = time.time() + seconds
    while time.time() < deadline:
        try:
            if httpx.get(url + "/health", timeout=1).status_code == 200:
                return
        except httpx.HTTPError:
            time.sleep(0.2)
    raise SystemExit(f"{url} did not start")


def main() -> None:
    radar_port, telco_port = free_port(), free_port()
    radar_url, telco_url = f"http://127.0.0.1:{radar_port}", f"http://127.0.0.1:{telco_port}"
    env = {**os.environ, "PYTHONPATH": ""}
    serve = [sys.executable, "-m", "uvicorn", "--factory", "src.main:create_app", "--log-level", "warning"]
    db = ROOT / "backend" / "record_tmp.db"
    db.unlink(missing_ok=True)
    radar = subprocess.Popen(serve + ["--port", str(radar_port)], cwd=ROOT / "backend", stdin=subprocess.DEVNULL,
                             env={**env, "DATABASE_URL": f"sqlite:///{db}", "TELCO_URL": telco_url, "RADAR_DEMO_TRUSTED_PREFIX": "5111"})
    telco = subprocess.Popen(serve + ["--port", str(telco_port)], cwd=ROOT / "simulator", stdin=subprocess.DEVNULL,
                             env={**env, "RADAR_URL": radar_url, "SIM_SPEED": "0", "SIM_SEED": "42"})
    try:
        wait_healthy(radar_url)
        wait_healthy(telco_url)
        population = httpx.get(f"{telco_url}/population").json()
        result = httpx.post(f"{telco_url}/scenarios/blast", params={"wait": "true"}, timeout=120).json()
        events = httpx.get(f"{telco_url}/history").json()
        data = {
            "_comment": "Recorded by scripts/record_fallback.py from a real run (simulator + radar). Replayed by the dashboard when the live connection is down.",
            "population": population, "events": events, "metrics": result["metrics"],
        }
        OUT.write_text(json.dumps(data, separators=(",", ":")), encoding="utf-8")
        print(f"recorded {len(events)} events and {len(population)} phones to {OUT.relative_to(ROOT)}")
        print("headline:", result["metrics"])
    finally:
        for p in (radar, telco):
            p.terminate()
            try:
                p.wait(timeout=10)
            except subprocess.TimeoutExpired:
                p.kill()
        db.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
