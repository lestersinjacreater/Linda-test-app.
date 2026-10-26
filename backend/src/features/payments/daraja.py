"""Daraja STK Push, SANDBOX ONLY (root principle 10). The host is fixed here, not a setting, so no configuration
can point Linda at live M-Pesa.

STK Push asks the payer's phone to confirm a payment with their PIN: Linda never sees or handles the PIN.
"""
import base64
from datetime import datetime, timedelta, timezone

import httpx

SANDBOX = "https://sandbox.safaricom.co.ke"
NAIROBI = timezone(timedelta(hours=3))  # Daraja's timestamps are Kenyan time


class DarajaError(Exception):
    pass


def timestamp(now: float) -> str:
    return datetime.fromtimestamp(now, NAIROBI).strftime("%Y%m%d%H%M%S")


def password(shortcode: str, passkey: str, ts: str) -> str:
    """Daraja's request password: base64 of shortcode + passkey + timestamp."""
    return base64.b64encode(f"{shortcode}{passkey}{ts}".encode()).decode()


def access_token(client: httpx.Client, key: str, secret: str) -> str:
    basic = base64.b64encode(f"{key}:{secret}".encode()).decode()
    r = client.get(f"{SANDBOX}/oauth/v1/generate", params={"grant_type": "client_credentials"},
                   headers={"Authorization": f"Basic {basic}"})
    if r.status_code != 200 or "access_token" not in r.json():
        raise DarajaError(f"sandbox login failed ({r.status_code})")
    return r.json()["access_token"]


def stk_push(client: httpx.Client, settings, payer: str, amount: int, reference: str, now: float) -> dict:
    """Asks `payer` (2547XXXXXXXX) to pay `amount` to the sandbox shortcode. Returns Daraja's answer."""
    token = access_token(client, settings.daraja_consumer_key, settings.daraja_consumer_secret)
    ts = timestamp(now)
    body = {
        "BusinessShortCode": settings.daraja_shortcode,
        "Password": password(settings.daraja_shortcode, settings.daraja_passkey, ts),
        "Timestamp": ts,
        "TransactionType": "CustomerPayBillOnline",
        "Amount": amount,
        "PartyA": payer,
        "PartyB": settings.daraja_shortcode,
        "PhoneNumber": payer,
        "CallBackURL": settings.daraja_callback_url,
        "AccountReference": reference[:12],   # Daraja allows 12 characters
        "TransactionDesc": "Linda demo",
    }
    r = client.post(f"{SANDBOX}/mpesa/stkpush/v1/processrequest", json=body, headers={"Authorization": f"Bearer {token}"})
    if r.status_code != 200:
        raise DarajaError(f"sandbox refused the request ({r.status_code})")
    return r.json()
