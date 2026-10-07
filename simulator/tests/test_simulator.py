from tests.conftest import FakeRadar

REPORT_KEYS = {"sender", "category", "confidence", "fingerprint", "model_version", "device_id", "sent_at"}


async def run_blast(make_app, **kw):
    app, radar = make_app(**kw)
    engine = app.state.engine
    engine.start("blast")
    await engine.wait()
    return app, radar, engine


async def test_blast_is_detected_confirmed_and_warned(make_app):
    app, radar, engine = await run_blast(make_app)
    m = engine.metrics()
    assert m["delivered"] == 240
    assert m["confirmed_after_s"] is not None
    assert m["first_detection_after_s"] is not None and m["first_detection_after_s"] < m["confirmed_after_s"]
    assert m["warned_by_network"] > 0 and m["warned_by_linda"] > 0  # early recipients; after confirmation the network warns at delivery, which is earlier
    assert m["warned_before_read_pct"] > 50
    assert m["feature_phones_warned_before_read_pct"] > 50  # basic phones protected by the network warning


async def test_linda_phones_really_detect_the_fake_mpesa_with_the_real_model(make_app):
    app, radar, engine = await run_blast(make_app)
    detected = [e for e in app.state.bus.history if e["type"] == "linda_detected"]
    assert detected and all(e["level"] == "SCAM" and e["category"] == "fake_mpesa" for e in detected)
    assert all(e["score"] >= 0.8 for e in detected)


async def test_reports_follow_the_contract_and_carry_no_message_text(make_app):
    app, radar, engine = await run_blast(make_app)
    assert radar.payloads
    for p in radar.payloads:
        assert set(p) == REPORT_KEYS
        assert len(p["fingerprint"]) == 16 and p["device_id"].startswith("5111")
        assert "Confirmed" not in str(p) and "Ksh" not in str(p)  # nothing from the message leaks


async def test_same_seed_gives_the_same_demo_every_run(make_app):
    app, radar = make_app()
    engine = app.state.engine
    results = []
    for _ in range(2):
        engine.start("blast")
        await engine.wait()
        m = engine.metrics()
        m.pop("sender"); m.pop("run")
        results.append(m)
    assert results[0] == results[1]
    assert len({p["sender"] for p in radar.payloads}) == 2  # but each run uses a fresh sender number


async def test_events_use_only_the_documented_types(make_app):
    app, radar, engine = await run_blast(make_app)
    allowed = {"reset", "blast_started", "sms_delivered", "linda_detected", "report_sent", "report_failed",
               "sender_confirmed", "warning_delivered", "blast_finished"}
    types = {e["type"] for e in app.state.bus.history}
    assert types <= allowed and {"blast_started", "sms_delivered", "linda_detected", "report_sent",
                                 "sender_confirmed", "warning_delivered", "blast_finished"} <= types
    times = [e["sim_time"] for e in app.state.bus.history]
    assert times == sorted(times)  # events arrive in simulated-time order
    assert all("text" not in e and "body" not in e for e in app.state.bus.history)


async def test_radar_offline_does_not_crash_and_nothing_is_confirmed(make_app):
    app, radar, engine = await run_blast(make_app, radar=FakeRadar(online=False))
    types = [e["type"] for e in app.state.bus.history]
    assert "report_failed" in types and "sender_confirmed" not in types
    assert engine.metrics()["confirmed_after_s"] is None
    assert engine.metrics()["warned_by_linda"] > 0  # on-device protection still works with no server


async def test_poison_attack_is_blocked(make_app):
    app, radar = make_app()
    engine = app.state.engine
    engine.start("poison")
    await engine.wait()
    assert engine.summary["poison_blocked"] is True
    assert len(radar.payloads) == 20 and not any(p["device_id"].startswith("5111") for p in radar.payloads)


async def test_rotating_numbers_each_get_handled(make_app):
    app, radar = make_app()
    engine = app.state.engine
    info = engine.start("rotating")
    await engine.wait()
    assert len(info["senders"]) == 3
    for s in info["senders"]:
        assert engine.metrics(s)["delivered"] > 0
    assert len({p["fingerprint"][:2] for p in radar.payloads}) >= 1


async def test_only_one_scenario_at_a_time_and_reset(make_app, client_for):
    app, radar = make_app(sim_speed=1.0)  # slow, so the first run is still going
    async with client_for(app) as c:
        assert (await c.post("/scenarios/blast")).status_code == 200
        assert (await c.post("/scenarios/blast")).status_code == 409
        assert (await c.post("/reset")).status_code == 200
        assert (await c.post("/scenarios/poison")).status_code == 200
        assert (await c.post("/reset")).status_code == 200
        assert (await c.post("/scenarios/call")).status_code == 501


async def test_network_endpoints(make_app, client_for):
    app, radar, engine = await run_blast(make_app)
    sender = engine.latest_sender
    async with client_for(app) as c:
        recipients = (await c.get("/deliveries", params={"sender": sender, "since": 0})).json()["recipients"]
        assert len(recipients) == 240 and all(r.startswith("254700") for r in recipients)
        later = (await c.get("/deliveries", params={"sender": sender, "since": 60})).json()["recipients"]
        assert 0 < len(later) < len(recipients)
        assert (await c.post("/network/confirm", json={"sender": sender})).json() == {"warned_now": 0}  # already done
        assert (await c.get("/metrics")).json()["delivered"] == 240
        assert (await c.get("/health")).json()["ok"] is True


def test_events_websocket_streams_new_events(make_app):
    from fastapi.testclient import TestClient
    app, _ = make_app()
    with TestClient(app) as client, client.websocket_connect("/events") as ws:
        client.post("/reset")
        assert ws.receive_json()["type"] == "reset"
