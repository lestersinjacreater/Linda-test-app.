"""End to end: the REAL radar and the REAL simulator as separate processes, talking only over HTTP.

    python -m pytest tests/e2e -v        (or: make test-e2e)

Proves the contracts hold between components (5.1 report, 5.4 confirm, 5.5 metrics) and that the
demo is repeatable. Neither component imports the other: they are started with `uvicorn`.
"""
import os
import socket
import subprocess
import sys
import time
from pathlib import Path

import httpx
import pytest

ROOT = Path(__file__).resolve().parents[2]


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def wait_healthy(url: str, proc: subprocess.Popen, seconds: float = 30.0) -> None:
    deadline = time.time() + seconds
    while time.time() < deadline:
        if proc.poll() is not None:
            raise RuntimeError(f"{url} exited early with code {proc.returncode}")
        try:
            if httpx.get(url + "/health", timeout=1).status_code == 200:
                return
        except httpx.HTTPError:
            time.sleep(0.2)
    raise RuntimeError(f"{url} did not become healthy")


@pytest.fixture(scope="module")
def stack(tmp_path_factory):
    radar_port, telco_port = free_port(), free_port()
    db = tmp_path_factory.mktemp("radar") / "radar.db"
    base_env = {**os.environ, "PYTHONPATH": ""}
    serve = [sys.executable, "-m", "uvicorn", "--factory", "src.main:create_app", "--log-level", "warning"]
    radar = subprocess.Popen(
        serve + ["--port", str(radar_port)], cwd=ROOT / "backend", stdin=subprocess.DEVNULL,
        env={**base_env, "DATABASE_URL": f"sqlite:///{db}", "TELCO_URL": f"http://127.0.0.1:{telco_port}",
             "RADAR_DEMO_TRUSTED_PREFIX": "5111"})
    telco = subprocess.Popen(
        serve + ["--port", str(telco_port)], cwd=ROOT / "simulator", stdin=subprocess.DEVNULL,
        env={**base_env, "RADAR_URL": f"http://127.0.0.1:{radar_port}", "SIM_SPEED": "0", "SIM_SEED": "42"})
    try:
        wait_healthy(f"http://127.0.0.1:{radar_port}", radar)
        wait_healthy(f"http://127.0.0.1:{telco_port}", telco)
        yield f"http://127.0.0.1:{radar_port}", f"http://127.0.0.1:{telco_port}"
    finally:
        for p in (radar, telco):
            p.terminate()
            try:
                p.wait(timeout=10)
            except subprocess.TimeoutExpired:
                p.kill()


def run(telco: str, scenario: str) -> dict:
    r = httpx.post(f"{telco}/scenarios/{scenario}", params={"wait": "true"}, timeout=60)
    assert r.status_code == 200, r.text
    return r.json()


def test_blast_confirms_on_the_real_radar_and_warns_the_network(stack):
    radar, telco = stack
    result = run(telco, "blast")
    m, sender = result["metrics"], result["senders"][0]
    assert m["confirmed_after_s"] is not None, "the real radar never confirmed: check trust settings"
    assert m["feature_phones_warned_before_read_pct"] > 90
    assert httpx.get(f"{radar}/v1/numbers/{sender}/risk").json()["status"] == "confirmed"
    assert sender in [b["msisdn"] for b in httpx.get(f"{radar}/v1/blocklist").json()["added"]]


def test_blast_is_repeatable(stack):
    _, telco = stack
    a, b = run(telco, "blast")["metrics"], run(telco, "blast")["metrics"]
    for k in ("delivered", "warned", "warned_before_read_pct", "feature_phones_warned_before_read_pct",
              "first_detection_after_s", "confirmed_after_s"):
        assert a[k] == b[k], k


def test_poison_attack_does_not_confirm_an_innocent_number_on_the_real_radar(stack):
    radar, telco = stack
    result = run(telco, "poison")
    assert result["metrics"]["poison_blocked"] is True
    victim = result["senders"][0]
    assert httpx.get(f"{radar}/v1/numbers/{victim}/risk").json()["status"] != "confirmed"


def test_rotating_numbers_all_get_confirmed(stack):
    radar, telco = stack
    result = run(telco, "rotating")
    statuses = [httpx.get(f"{radar}/v1/numbers/{s}/risk").json()["status"] for s in result["senders"]]
    assert statuses == ["confirmed"] * 3
