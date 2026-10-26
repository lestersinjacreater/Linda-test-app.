"""Payment precheck (root CLAUDE.md 5.7) and the Daraja SANDBOX STK Push behind it."""
import base64
import json
from dataclasses import replace

import httpx
from fastapi.testclient import TestClient

from src.core.config import Settings
from src.main import create_app
from tests.conftest import Radar, device


def confirm(radar: Radar, sender: str) -> None:
    for n in range(1, 4):
        radar.report(sender="254700000199", device=device(n))
        radar.set_trust(device(n), 1.0)
    for n in range(1, 4):
        radar.report(sender=sender, device=device(n), confidence=0.99)
    assert radar.risk(sender)["status"] == "confirmed"


def precheck(radar: Radar, recipient: str, amount=1500, **extra):
    return radar.client.post("/v1/payments/precheck", json={"recipient": recipient, "amount": amount, **extra})


# ---------- precheck ----------
def test_a_confirmed_scammer_is_blocked_with_a_reason_in_both_languages():
    radar = Radar()
    confirm(radar, "254700000050")
    r = precheck(radar, "254700000050").json()
    assert r["allow"] is False and r["status"] == "confirmed"
    assert "scam" in r["reason"].lower() and r["reason_sw"]


def test_an_unknown_number_is_allowed_but_never_called_safe():
    r = precheck(Radar(), "254700000051").json()
    assert r["allow"] is True and r["status"] == "unknown"
    assert "safe" not in r["reason"].lower()


def test_a_suspected_number_is_allowed_with_a_caution():
    radar = Radar()
    radar.report(sender="254700000052", device=device(1))
    radar.report(sender="254700000052", device=device(2))
    r = precheck(radar, "254700000052").json()
    assert r["status"] == "suspected" and r["allow"] is True and "reported" in r["reason"].lower()


def test_a_verified_number_is_allowed_and_named():
    r = precheck(Radar(), "254722000000").json()
    assert r["allow"] is True and r["status"] == "verified" and "Safaricom Customer Care" in r["reason"]


def test_bad_requests_are_refused():
    radar = Radar()
    assert precheck(radar, "0712345678").status_code == 422          # numbers are 2547XXXXXXXX inside the system
    assert precheck(radar, "254700000051", amount=0).status_code == 422
    assert precheck(radar, "254700000051", amount=300_000).status_code == 422
    assert precheck(radar, "254700000051", note="hi").status_code == 422  # unknown fields are refused


def test_nobody_can_sweep_the_list_through_the_precheck():
    radar = Radar(lookup_rate_per_hour=3)
    for _ in range(3):
        assert precheck(radar, "254700000051").status_code == 200
    assert precheck(radar, "254700000051").status_code == 429


# ---------- STK Push (Daraja sandbox) ----------
class FakeDaraja:
    """Stands in for sandbox.safaricom.co.ke: records what Linda sends."""

    def __init__(self):
        self.calls: list[httpx.Request] = []

    def __call__(self, request: httpx.Request) -> httpx.Response:
        self.calls.append(request)
        if request.url.path == "/oauth/v1/generate":
            return httpx.Response(200, json={"access_token": "tok", "expires_in": "3599"})
        if request.url.path == "/mpesa/stkpush/v1/processrequest":
            return httpx.Response(200, json={"MerchantRequestID": "m1", "CheckoutRequestID": "ws_CO_1",
                                             "ResponseCode": "0", "CustomerMessage": "Success. Request accepted"})
        return httpx.Response(404)


def daraja_radar(fake: FakeDaraja, **overrides) -> TestClient:
    settings = replace(Settings(database_url="sqlite:///:memory:"), daraja_consumer_key="key", daraja_consumer_secret="secret",
                       daraja_shortcode="174379", daraja_passkey="passkey",
                       daraja_callback_url="https://linda.example/radar/v1/payments/stk/callback",
                       daraja_allowed_payers=("254711000001",), **overrides)
    app = create_app(settings, notifier=lambda s: None, clock=lambda: 1_800_000_000.0,
                     daraja_client=httpx.Client(transport=httpx.MockTransport(fake)))
    return TestClient(app)


def stk(client: TestClient, recipient="254700000051", amount=1500, payer="254711000001"):
    return client.post("/v1/payments/stk", json={"payer": payer, "recipient": recipient, "amount": amount})


def test_stk_push_is_refused_when_the_sandbox_is_not_configured():
    r = Radar().client.post("/v1/payments/stk", json={"payer": "254711000001", "recipient": "254700000051", "amount": 10})
    assert r.status_code == 503


def test_stk_push_goes_to_the_sandbox_with_the_right_password():
    fake = FakeDaraja()
    r = stk(daraja_radar(fake))
    assert r.status_code == 200 and r.json()["checkout_request_id"] == "ws_CO_1"
    oauth, push = fake.calls
    assert oauth.url.host == push.url.host == "sandbox.safaricom.co.ke"  # sandbox only, never production
    assert oauth.headers["authorization"] == "Basic " + base64.b64encode(b"key:secret").decode()
    body = json.loads(push.content)
    assert push.headers["authorization"] == "Bearer tok"
    assert body["Password"] == base64.b64encode(f"174379passkey{body['Timestamp']}".encode()).decode()
    assert body["Timestamp"] == "20270115110000"  # Nairobi time of the fake clock
    assert body["PartyA"] == body["PhoneNumber"] == "254711000001" and body["Amount"] == 1500
    assert body["BusinessShortCode"] == body["PartyB"] == "174379"


def test_stk_push_rechecks_the_recipient_on_the_server():
    fake = FakeDaraja()
    client = daraja_radar(fake)
    radar = Radar()  # reuse its helpers on this app: three trusted phones confirm 254700000777
    radar.app, radar.client = client.app, client
    confirm(radar, "254700000777")
    r = stk(client, recipient="254700000777")
    assert r.status_code == 403 and fake.calls == []  # never asks the payer to pay a confirmed scammer


def test_only_the_teams_phones_get_a_payment_prompt():
    fake = FakeDaraja()
    r = stk(daraja_radar(fake), payer="254711000099")
    assert r.status_code == 403 and fake.calls == []


def test_the_callback_is_acknowledged():
    client = daraja_radar(FakeDaraja())
    r = client.post("/v1/payments/stk/callback", json={"Body": {"stkCallback": {"ResultCode": 0, "CheckoutRequestID": "ws_CO_1"}}})
    assert r.status_code == 200 and r.json()["ResultCode"] == 0
