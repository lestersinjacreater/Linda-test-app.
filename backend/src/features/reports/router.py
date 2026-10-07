"""POST /v1/reports (contract 5.1). Phones send NO message text: extra fields are rejected."""
import re
from datetime import datetime
from typing import Optional

from fastapi import APIRouter, BackgroundTasks, HTTPException, Request
from pydantic import BaseModel, ConfigDict, Field, field_validator

from src.features.confirmation.service import RateLimited, submit_report

CATEGORIES = {
    "sent_by_mistake", "fake_mpesa", "prize", "fuliza_upgrade", "kra_refund",
    "job_fee", "loan_fee", "pin_request", "phishing_link", "other",
}
_MSISDN = re.compile(r"254[17]\d{8}")
_HEX = re.compile(r"[0-9a-f]+")

router = APIRouter()


class ReportIn(BaseModel):
    model_config = ConfigDict(extra="forbid")  # a stray "text" field is an error, not silently kept

    sender: str
    category: str
    confidence: float = Field(ge=0.0, le=1.0)
    fingerprint: str
    model_version: str = Field(min_length=1, max_length=32)
    device_id: str = Field(min_length=8, max_length=64)
    integrity_token: Optional[str] = Field(default=None, max_length=4096)
    sent_at: datetime

    @field_validator("category")
    @classmethod
    def known_category(cls, v: str) -> str:
        if v not in CATEGORIES:
            raise ValueError(f"category must be one of {sorted(CATEGORIES)}")
        return v

    @field_validator("fingerprint")
    @classmethod
    def sixteen_hex(cls, v: str) -> str:
        if len(v) != 16 or not _HEX.fullmatch(v):
            raise ValueError("fingerprint must be 16 lowercase hex characters")
        return v

    @field_validator("device_id")
    @classmethod
    def hex_device(cls, v: str) -> str:
        if not _HEX.fullmatch(v):
            raise ValueError("device_id must be lowercase hex")
        return v


class ReportOut(BaseModel):
    status: str
    devices: int


@router.post("/v1/reports", response_model=ReportOut)
def post_report(report: ReportIn, background: BackgroundTasks, request: Request) -> ReportOut:
    app = request.app
    # Verified sender IDs (MPESA, KPLC...) are accepted and ignored; anything else must be a real-format number.
    if report.sender.upper() not in app.state.verified and not _MSISDN.fullmatch(report.sender):
        raise HTTPException(422, "sender must look like 2547XXXXXXXX")
    with app.state.sessions() as session:
        try:
            result = submit_report(session, app.state.settings, app.state.verified, report, app.state.clock())
        except RateLimited:
            raise HTTPException(429, "too many reports from this device; try again later")
    if result.newly_confirmed:
        background.add_task(app.state.notifier, report.sender)
    return ReportOut(status=result.status, devices=result.devices)
