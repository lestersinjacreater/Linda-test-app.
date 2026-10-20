"""Sends the network's warning as a REAL SMS to the team's own basic phone on the demo table.

Only the WARNING is ever sent, never the scam text: we do not send scam messages to real people,
and the warning starts with "[Linda demo]" so it does not pose as an official Safaricom message.
Africa's Talking is reached with one plain HTTP request (their documented messaging API), so no SDK.
"""
from typing import Protocol

import httpx

SANDBOX_URL = "https://api.sandbox.africastalking.com/version1/messaging"
LIVE_URL = "https://api.africastalking.com/version1/messaging"


class SmsSender(Protocol):
    async def send(self, msisdn: str, message: str) -> str:
        """Returns "sent", "failed" or "skipped"."""


class NoSms:
    """No Africa's Talking key in .env: nothing leaves the simulator, and the demo carries on."""

    async def send(self, msisdn: str, message: str) -> str:
        return "skipped"


class AfricasTalkingSms:
    def __init__(self, username: str, api_key: str, sender_id: str = "", client: httpx.AsyncClient | None = None):
        self.username, self.api_key, self.sender_id = username, api_key, sender_id
        self.url = SANDBOX_URL if username == "sandbox" else LIVE_URL
        self._http = client or httpx.AsyncClient(timeout=10.0)

    async def send(self, msisdn: str, message: str) -> str:
        form = {"username": self.username, "to": "+" + msisdn, "message": message}
        if self.sender_id:
            form["from"] = self.sender_id
        try:
            r = await self._http.post(self.url, data=form, headers={"apiKey": self.api_key, "Accept": "application/json"})
            r.raise_for_status()
            recipients = r.json()["SMSMessageData"]["Recipients"]
            return "sent" if recipients and recipients[0].get("status") == "Success" else "failed"
        except Exception:
            return "failed"


def sender_from(settings) -> SmsSender:
    return AfricasTalkingSms(settings.at_username, settings.at_api_key, settings.at_sender_id) if settings.at_api_key else NoSms()


def display_number(msisdn: str) -> str:
    """254700900011 -> 0700 900 011, how people in Kenya write a number."""
    local = "0" + msisdn[3:]
    return f"{local[:4]} {local[4:7]} {local[7:]}"


def warning_text(sender: str, channel: str) -> str:
    """The real SMS. Short enough for one SMS (160 characters); no message text, no evidence."""
    what = "just called you" if channel == "after_call" else "just sent you an SMS"
    return (f"[Linda demo] {display_number(sender)} {what} and is reported as a scam. "
            "Do not send money or share your PIN. Forward scam SMS to 333.")
