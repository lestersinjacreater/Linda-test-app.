"""Protecting phones WITHOUT Linda: adoption levels, scam calls, and the real basic phone (root CLAUDE.md item 6)."""
import pytest

from src.features.adoption.odds import chance_of_at_least, odds
from src.features.population.population import build_adoption_population
from src.features.realphones.sms import AfricasTalkingSms, warning_text
from tests.conftest import FakeRadar, FakeSms

LEVELS = (0.5, 1, 2)
REAL = "254799000001"  # placeholder for a team phone; real numbers live only in .env


async def run(make_app, scenario="blast", adoption=None, caller=None, **kw):
    app, radar = make_app(**kw)
    engine = app.state.engine
    info = engine.start(scenario, adoption=adoption, caller=caller)
    await engine.wait()
    return app, radar, engine, info


def events(app, type_):
    return [e for e in app.state.bus.history if e["type"] == type_]


# ------------------------------------------------------------------ adoption levels
def test_lower_adoption_users_are_always_inside_the_higher_levels():
    linda = {pct: {p.id for p in build_adoption_population(42, 1, 2000, pct, 0.6, "5111") if p.kind == "linda"}
             for pct in LEVELS}
    assert [len(linda[p]) for p in LEVELS] == [10, 20, 40]  # percent of ALL people
    assert linda[0.5] <= linda[1] <= linda[2]


def test_only_smartphones_adopt_and_everything_else_stays_the_same():
    a = build_adoption_population(42, 1, 2000, 0.5, 0.6, "5111")
    b = build_adoption_population(42, 1, 2000, 2, 0.6, "5111")
    for x, y in zip(a, b):
        assert (x.town, x.lat, x.lon, x.read_delay_s, x.msisdn) == (y.town, y.lat, y.lon, y.read_delay_s, y.msisdn)
        if x.kind != y.kind:  # the only difference: a smartphone that installed Linda
            assert (x.kind, y.kind) == ("smart", "linda")


async def test_same_blast_reaches_the_same_people_at_every_level(make_app):
    seen = {}
    for pct in LEVELS:
        app, _, engine, _ = await run(make_app, adoption=pct)
        seen[pct] = [p.id for p, _ in engine.deliveries[engine.latest_sender]]
        assert len(seen[pct]) == 500 and engine.metrics()["adoption_pct"] == pct
    assert seen[0.5] == seen[1] == seen[2]


async def test_more_adoption_protects_at_least_as_many_people(make_app):
    m = {}
    for pct in LEVELS:
        _, _, engine, _ = await run(make_app, adoption=pct)
        m[pct] = engine.metrics()
    assert m[0.5]["linda_recipients"] <= m[1]["linda_recipients"] <= m[2]["linda_recipients"]
    before = [m[p]["warned_before_read_pct"] or 0 for p in LEVELS]
    assert before == sorted(before)
    assert m[2]["confirmed_after_s"] is not None and m[2]["warned_before_read_pct"] > 80


async def test_without_the_query_the_classic_demo_is_unchanged(make_app):
    _, _, engine, _ = await run(make_app)
    m = engine.metrics()
    assert m["delivered"] == 240 and m["adoption_pct"] is None and len(engine.phones) == 300


def test_odds_match_the_binomial_maths():
    r = odds(LEVELS, trials=1500, recipients=500, min_reports=3, seed=42, duration_s=90.0, network_latency_s=1.0)
    for level in r["levels"]:
        assert abs(level["confirmed_pct"] - level["confirmed_pct_binomial"]) < 4.0
        assert level["warned_before_read_pct"] >= level["warned_before_read_pct_linda_only"]
    assert round(100 * chance_of_at_least(3, 500, 0.01)) == 88  # the pitch figure
    confirmed = [lv["confirmed_pct"] for lv in r["levels"]]
    assert confirmed == sorted(confirmed) and "catches it" in r["assumes"]


async def test_odds_endpoint_and_bad_input(make_app, client_for):
    app, _ = make_app()
    async with client_for(app) as c:
        r = await c.get("/adoption/odds", params={"trials": 200})
        assert r.status_code == 200 and [lv["adoption_pct"] for lv in r.json()["levels"]] == [0.5, 1, 2]
        assert (await c.get("/adoption/odds", params={"levels": "abc"})).status_code == 422
        assert (await c.get("/adoption/odds", params={"levels": "0"})).status_code == 422
        assert (await c.post("/scenarios/blast", params={"adoption": 0})).status_code == 422
        assert (await c.post("/scenarios/blast", params={"adoption": 101})).status_code == 422


# ------------------------------------------------------------------ scam calls
async def test_calls_after_the_sms_are_labelled_or_warned(make_app):
    app, _, engine, info = await run(make_app, scenario="call")
    sender = info["senders"][0]
    calls = engine.metrics(sender)["calls"]
    assert calls["placed"] == 120 + 40
    assert calls["labelled_before_answer"] > 0 and calls["warned_after_call"] > 0
    assert calls["protected_pct"] > 80
    assert events(app, "call_attempt") and events(app, "call_labelled")
    after = [e for e in events(app, "warning_delivered") if e["channel"] == "after_call"]
    assert after and all(e["kind"] != "linda" for e in after)


async def test_the_sms_part_of_the_call_scenario_is_exactly_the_blast(make_app):
    _, _, blast, _ = await run(make_app)
    _, _, call, _ = await run(make_app, scenario="call")
    a, b = blast.metrics(), call.metrics()
    for key in ("delivered", "confirmed_after_s", "first_detection_after_s"):
        assert a[key] == b[key]


async def test_no_after_call_warning_for_calls_before_confirmation(make_app):
    app, _, engine, info = await run(make_app, scenario="call", radar=FakeRadar(online=False))
    calls = engine.metrics(info["senders"][0])["calls"]
    assert calls["warned_after_call"] == 0 and calls["unprotected"] > 0
    labels = {e["label"] for e in events(app, "call_labelled")}
    assert labels <= {"recent_scam_message"}  # Linda phones still know from their own detection


async def test_a_number_the_gateway_already_knows_is_warned_from_the_first_call(make_app):
    known = "254700000777"
    radar = FakeRadar(known={known: "confirmed"})
    app, radar, engine, info = await run(make_app, scenario="call", caller=known, radar=radar)
    assert radar.risk_lookups == [known] and not radar.payloads  # asked once; calls carry no reports
    calls = engine.metrics(known)["calls"]
    assert calls["unprotected"] == 0 and calls["protected_pct"] == 100.0
    assert events(app, "sender_confirmed")[0]["by"] == "gateway"


async def test_a_suspected_caller_gets_the_softer_label_and_no_network_warning(make_app):
    radar = FakeRadar(known={"254700000555": "suspected"})
    app, _, engine, _ = await run(make_app, scenario="call", caller="254700000555", radar=radar)
    assert {e["label"] for e in events(app, "call_labelled")} == {"suspected"}
    assert engine.metrics("254700000555")["calls"]["warned_after_call"] == 0


async def test_caller_must_be_a_kenyan_number(make_app, client_for):
    app, _ = make_app()
    async with client_for(app) as c:
        assert (await c.post("/scenarios/call", params={"caller": "0700000777"})).status_code == 422


# ------------------------------------------------------------------ the real basic phone
async def test_the_real_phone_gets_exactly_one_warning_and_never_the_scam(make_app):
    sms = FakeSms()
    app, _, engine, _ = await run(make_app, sms=sms, real_phones=(REAL,))
    assert len(sms.sent) == 1
    to, message = sms.sent[0]
    assert to == REAL and message.startswith("[Linda demo]")
    assert "Confirmed" not in message and "Ksh" not in message and "M-PESA" not in message
    assert engine.metrics()["real_phone_sms"][0]["status"] == "sent"
    real = [e for e in events(app, "warning_delivered") if e["real_sms"]]
    assert len(real) == 1 and "254799" not in str(app.state.bus.history)  # the real number never reaches the dashboard


async def test_real_phones_do_not_change_the_simulated_run(make_app):
    _, _, plain, _ = await run(make_app)
    _, _, with_real, _ = await run(make_app, sms=FakeSms(), real_phones=(REAL,))
    a, b = plain.metrics(), with_real.metrics()
    assert b["delivered"] == a["delivered"] + 1 and b["confirmed_after_s"] == a["confirmed_after_s"]


async def test_with_no_key_the_demo_still_runs(make_app):
    app, _, engine, _ = await run(make_app, real_phones=(REAL,))  # default sender: no AT key in tests
    assert engine.metrics()["real_phone_sms"][0]["status"] == "skipped"


async def test_africas_talking_request_shape():
    import httpx
    seen = {}

    def handler(request):
        seen["url"], seen["key"], seen["body"] = str(request.url), request.headers["apiKey"], request.content.decode()
        return httpx.Response(201, json={"SMSMessageData": {"Recipients": [{"status": "Success", "number": "+" + REAL}]}})

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    status = await AfricasTalkingSms("sandbox", "test-key", client=client).send(REAL, warning_text("254700900011", "sms"))
    assert status == "sent" and "sandbox" in seen["url"] and seen["key"] == "test-key"
    assert "to=%2B254799000001" in seen["body"] and "username=sandbox" in seen["body"]


@pytest.mark.parametrize("channel", ["sms", "after_call"])
def test_warning_fits_in_one_sms(channel):
    assert len(warning_text("254700900011", channel)) <= 160
