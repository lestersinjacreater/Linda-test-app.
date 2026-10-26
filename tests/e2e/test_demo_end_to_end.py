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
             "RADAR_DEMO_TRUSTED_PREFIX": "5111", "RADAR_DEMO_TRUSTED_TRUST": "0.85"})  # as docker-compose
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


def run(telco: str, scenario: str, **params) -> dict:
    r = httpx.post(f"{telco}/scenarios/{scenario}", params={"wait": "true", **params}, timeout=60)
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


def test_three_demo_phones_are_enough_for_the_real_gateway(stack):
    """The pitch says 3 independent Linda phones. With demo trust 0.85 the real gateway agrees."""
    _, telco = stack
    run(telco, "blast")
    history = httpx.get(f"{telco}/history").json()
    confirmed_at = next(i for i, e in enumerate(history) if e["type"] == "sender_confirmed")
    reports_before = [e for e in history[:confirmed_at] if e["type"] == "report_sent"]
    assert len(reports_before) == 3


def test_adoption_levels_on_the_real_gateway(stack):
    _, telco = stack
    m = {pct: run(telco, "blast", adoption=pct)["metrics"] for pct in ("0.5", "1", "2")}
    assert all(x["delivered"] == 500 for x in m.values())
    assert m["0.5"]["linda_recipients"] <= m["1"]["linda_recipients"] <= m["2"]["linda_recipients"]
    assert m["1"]["confirmed_after_s"] is not None and m["1"]["feature_phones_warned_before_read_pct"] > 90


def test_calls_from_a_number_the_gateway_confirmed_are_all_protected(stack):
    _, telco = stack
    scammer = run(telco, "blast")["senders"][0]  # the gateway now knows this number
    calls = run(telco, "call", caller=scammer)["metrics"]["calls"]
    assert calls["unprotected"] == 0 and calls["warned_after_call"] > 0


def test_sms_then_calls_on_the_real_gateway(stack):
    _, telco = stack
    calls = run(telco, "call")["metrics"]["calls"]
    assert calls["protected_pct"] > 80


def test_a_restarted_telco_still_confirms_on_the_same_gateway(tmp_path):
    """The gateway keeps what it learned. A telco that restarts (a deploy, a crash) must not reuse an already
    confirmed scammer number, or the network is never told again and the blast never ends (found 25 Oct)."""
    radar_port, telco_port = free_port(), free_port()
    env = {**os.environ, "PYTHONPATH": "", "NO_PROXY": "127.0.0.1,localhost", "no_proxy": "127.0.0.1,localhost"}
    serve = [sys.executable, "-m", "uvicorn", "--factory", "src.main:create_app", "--log-level", "warning"]

    def start_telco():
        p = subprocess.Popen(serve + ["--port", str(telco_port)], cwd=ROOT / "simulator", stdin=subprocess.DEVNULL,
                             env={**env, "RADAR_URL": f"http://127.0.0.1:{radar_port}", "SIM_SPEED": "0", "SIM_SEED": "42"})
        wait_healthy(f"http://127.0.0.1:{telco_port}", p)
        return p

    radar = subprocess.Popen(
        serve + ["--port", str(radar_port)], cwd=ROOT / "backend", stdin=subprocess.DEVNULL,
        env={**env, "DATABASE_URL": f"sqlite:///{tmp_path / 'radar.db'}", "TELCO_URL": f"http://127.0.0.1:{telco_port}",
             "RADAR_DEMO_TRUSTED_PREFIX": "5111", "RADAR_DEMO_TRUSTED_TRUST": "0.85"})
    telco = start_telco()
    try:
        wait_healthy(f"http://127.0.0.1:{radar_port}", radar)
        first = run(f"http://127.0.0.1:{telco_port}", "blast")
        telco.terminate()
        telco.wait(timeout=10)
        telco = start_telco()
        second = run(f"http://127.0.0.1:{telco_port}", "blast")
        assert second["senders"][0] != first["senders"][0]
        assert second["metrics"]["confirmed_after_s"] == first["metrics"]["confirmed_after_s"] is not None
    finally:
        for p in (radar, telco):
            p.terminate()
            try:
                p.wait(timeout=10)
            except subprocess.TimeoutExpired:
                p.kill()
