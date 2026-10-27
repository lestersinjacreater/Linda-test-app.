"""The fraud-team console's endpoints (contract 5.15). Off unless RADAR_FRAUD_TEAM_CONSOLE is on.

GET  /v1/fraud-team/console          what real phones reported: counts, review queue, patterns, activity
POST /v1/fraud-team/confirm {msisdn}  a person confirms a scam number (phones and the network are told)
POST /v1/fraud-team/clear   {msisdn}  a person says it is not a scam (phones drop it)

These show FULL numbers, so they are guarded twice: Caddy asks for the presenter password and then adds the internal
X-Fraud-Team-Key header (FRAUD_TEAM_KEY), which the gateway checks. Caddy removes that header on every other path,
so nobody can send it from outside.
"""
import hmac
import re
from types import SimpleNamespace

from fastapi import APIRouter, BackgroundTasks, HTTPException, Request
from pydantic import BaseModel, ConfigDict

from src.features.fraudteam.service import Refused, clear, confirm, console
from src.features.intel.sync import forward_item

router = APIRouter()
_MSISDN = re.compile(r"254[17]\d{8}")


class NumberIn(BaseModel):
    model_config = ConfigDict(extra="forbid")
    msisdn: str


def _guard(request: Request) -> None:
    settings = request.app.state.settings
    if not settings.fraud_team_console:
        raise HTTPException(404, "Not Found")
    key = settings.fraud_team_key
    if key and not hmac.compare_digest(request.headers.get("x-fraud-team-key", ""), key):
        raise HTTPException(403, "fraud-team key missing or wrong")


def _number(body: NumberIn) -> str:
    if not _MSISDN.fullmatch(body.msisdn):
        raise HTTPException(422, "msisdn must look like 2547XXXXXXXX")
    return body.msisdn


@router.get("/v1/fraud-team/console")
def get_console(request: Request) -> dict:
    _guard(request)
    return console(request.app)


@router.post("/v1/fraud-team/confirm")
def post_confirm(body: NumberIn, request: Request, background: BackgroundTasks) -> dict:
    _guard(request)
    msisdn = _number(body)
    app = request.app
    try:
        category = confirm(app, msisdn)
    except Refused as e:
        raise HTTPException(409, str(e))
    background.add_task(app.state.notifier, msisdn)  # the network warns everyone who heard from it
    item = SimpleNamespace(sender=msisdn, category=category)
    background.add_task(forward_item, app, "confirmation", item, app.state.clock())
    return {"msisdn": msisdn, "status": "confirmed"}


@router.post("/v1/fraud-team/clear")
def post_clear(body: NumberIn, request: Request) -> dict:
    _guard(request)
    msisdn = _number(body)
    clear(request.app, msisdn)
    return {"msisdn": msisdn, "status": "allowlisted"}
