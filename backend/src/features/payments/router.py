"""Payment precheck (root CLAUDE.md 5.7): before money moves, ask whether the recipient is a known scammer.

POST /v1/payments/precheck {recipient, amount} -> {allow, reason, reason_sw, status}
  - confirmed: allow false (the payment screen stops and explains); everything else: allow true, with a reason.
  - It never calls a number "safe": no reports is not proof.
POST /v1/payments/stk {payer, recipient, amount}: the demo payment, a Daraja SANDBOX STK Push. The server checks the
  recipient again first, so a payment to a confirmed scammer is never even proposed.
POST /v1/payments/stk/callback: where the sandbox reports the result; acknowledged, nothing stored.

Lowest priority in the project: Linda's story is the conversation, not the payment.
"""
from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel, ConfigDict, Field

from src.features.lookups.router import risk_of
from src.features.payments.daraja import DarajaError, stk_push

router = APIRouter()
MSISDN = r"^254[17]\d{8}$"
MAX_AMOUNT = 250_000  # the M-PESA limit for one transaction, in Ksh


class Precheck(BaseModel):
    model_config = ConfigDict(extra="forbid")
    recipient: str = Field(pattern=MSISDN)
    amount: int = Field(ge=1, le=MAX_AMOUNT)


class Stk(Precheck):
    payer: str = Field(pattern=MSISDN)


def decide(app, recipient: str) -> dict:
    risk = risk_of(app, recipient)
    status = risk["status"]
    if status == "confirmed":
        return {"allow": False, "status": status,
                "reason": "Stop: this number is a confirmed scam number. Do not send money to it.",
                "reason_sw": "Simama: namba hii ni ya ulaghai iliyothibitishwa. Usitume pesa kwake."}
    if status == "suspected":
        k = max(risk["reports"], 1)
        return {"allow": True, "status": status,
                "reason": f"Be careful: this number has been reported for fraud ({k} reports). Pay only if you know them.",
                "reason_sw": f"Kuwa mwangalifu: namba hii imeripotiwa kwa ulaghai (ripoti {k}). Lipa tu kama unamjua."}
    if status == "verified":
        return {"allow": True, "status": status,
                "reason": f"Verified: {risk['name_en']}.", "reason_sw": f"Imethibitishwa: {risk['name_sw']}."}
    return {"allow": True, "status": "unknown",
            "reason": "No reports about this number. Pay only people you know, and never share your PIN.",
            "reason_sw": "Hakuna ripoti kuhusu namba hii. Lipa tu watu unaowajua, na usitoe PIN yako."}


def _check_rate(request: Request) -> None:
    """Same limit as the private lookups: a payment screen needs a few checks, a sweep of the list needs millions."""
    key = "pay:" + (request.headers.get("X-Linda-Device") or (request.client.host if request.client else "?"))
    if not request.app.state.lookup_limiter.allow(key):
        raise HTTPException(429, "too many checks, try again later")


@router.post("/v1/payments/precheck")
def precheck(body: Precheck, request: Request) -> dict:
    _check_rate(request)
    return decide(request.app, body.recipient)


@router.post("/v1/payments/stk")
def stk(body: Stk, request: Request) -> dict:
    app = request.app
    s = app.state.settings
    if not (s.daraja_consumer_key and s.daraja_consumer_secret and s.daraja_passkey and s.daraja_callback_url):
        raise HTTPException(503, "Daraja sandbox is not configured (DARAJA_* in .env)")
    if body.payer not in s.daraja_allowed_payers:
        raise HTTPException(403, "this phone is not on the demo list (DARAJA_ALLOWED_PAYERS)")
    _check_rate(request)
    verdict = decide(app, body.recipient)
    if not verdict["allow"]:
        raise HTTPException(403, verdict["reason"])
    try:
        answer = stk_push(app.state.daraja_client, s, body.payer, body.amount, body.recipient[-9:], app.state.clock())
    except DarajaError as exc:
        raise HTTPException(502, str(exc))
    return {"checkout_request_id": answer.get("CheckoutRequestID"), "message": answer.get("CustomerMessage"),
            "precheck": verdict}


@router.post("/v1/payments/stk/callback")
async def stk_callback(request: Request) -> dict:
    await request.body()  # read and dropped: the demo shows the result on the payer's phone, nothing is stored
    return {"ResultCode": 0, "ResultDesc": "Accepted"}
