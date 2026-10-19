"""The fraud-intelligence contract (root CLAUDE.md 5.12) plus two presenter controls."""
import re
from datetime import datetime, timezone
from typing import Literal, Optional

from fastapi import APIRouter, HTTPException, Query, Request
from pydantic import BaseModel, ConfigDict, Field

from src.features.intel.store import STATUSES, iso

router = APIRouter()
_MSISDN = re.compile(r"254[17]\d{8}")


def _store(request: Request):
    return request.app.state.store


@router.get("/v1/intel/numbers")
def numbers(request: Request, since: Optional[datetime] = Query(default=None)) -> dict:
    store = _store(request)
    since_ts = None if since is None else (since if since.tzinfo else since.replace(tzinfo=timezone.utc)).timestamp()
    return {"as_of": iso(store.clock()), "numbers": [n.to_dict() for n in store.changed_since(since_ts)]}


@router.get("/v1/intel/verified")
def verified(request: Request) -> dict:
    return {"numbers": _store(request).verified}


@router.get("/v1/intel/campaigns")
def campaigns(request: Request) -> dict:
    return {"campaigns": _store(request).campaigns}


class Forwarded(BaseModel):
    """What the gateway forwards. `extra="forbid"`: a message text field is rejected outright."""
    model_config = ConfigDict(extra="forbid")
    kind: Literal["report", "confirmation"]
    sender: str = Field(pattern=r"^254[17]\d{8}$")
    category: str = Field(max_length=32)
    confidence: Optional[float] = Field(default=None, ge=0, le=1)
    fingerprint: Optional[str] = Field(default=None, pattern=r"^[0-9a-f]{16}$")
    model_version: Optional[str] = Field(default=None, max_length=32)
    device_id: Optional[str] = Field(default=None, max_length=64)
    at: str = Field(max_length=40)


@router.post("/v1/intel/reports")
def receive(item: Forwarded, request: Request) -> dict:
    _store(request).receive(item.model_dump())
    return {"ok": True}


@router.get("/v1/intel/reports")
def inbox(request: Request, limit: int = Query(default=50, ge=1, le=500)) -> dict:
    """The fraud team's inbox: what Linda has sent back (for the demo and the dashboard)."""
    return {"reports": list(_store(request).inbox)[:limit]}


class Flag(BaseModel):
    msisdn: str
    status: Literal["reported", "blocked", "campaign"]
    category: str = Field(default="other", max_length=32)
    campaign_id: Optional[str] = None


@router.post("/admin/numbers")
def flag(body: Flag, request: Request) -> dict:
    """Presenter: "the fraud team just blocked this number". Protected by the proxy in production."""
    if not _MSISDN.fullmatch(body.msisdn):
        raise HTTPException(422, "msisdn must look like 2547XXXXXXXX")
    if body.msisdn in {v["msisdn"] for v in _store(request).verified}:
        raise HTTPException(409, "a verified number cannot be flagged")
    assert body.status in STATUSES
    return _store(request).flag(body.msisdn, body.status, body.category, body.campaign_id).to_dict()


@router.post("/admin/reset")
def reset(request: Request) -> dict:
    _store(request).reset()
    return {"ok": True}
