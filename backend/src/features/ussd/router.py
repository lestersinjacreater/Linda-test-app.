"""USSD checker (root CLAUDE.md 5.6): anyone, on any phone, dials a code and checks a number before paying.

Africa's Talking calls POST /ussd with form fields. `text` is every answer so far joined by "*" ("" first, then
"1", then "1*0712345678"). The reply is plain text: "CON ..." keeps the menu open, "END ..." closes it.

Menu: 1 Check a number, 2 How to stay safe, 3 Kiswahili (the same menu in Kiswahili: "3*1", "3*2").
It answers from the same knowledge as GET /v1/numbers/{msisdn}/risk (5.2). It never says a number is "safe":
no reports does not mean genuine. Each phone may check LOOKUP_RATE_PER_HOUR numbers an hour, so nobody can
sweep the list of flagged numbers (root principle 6).
"""
import re
from urllib.parse import parse_qs

from fastapi import APIRouter, Request
from fastapi.responses import PlainTextResponse

from src.features.lookups.router import risk_of

router = APIRouter()

# A screen holds about 182 characters on most phones: every reply below fits.
EN = {
    "menu": "CON Linda: check a number before you pay\n1. Check a number\n2. How to stay safe\n3. Kiswahili",
    "ask": "CON Enter the number that called or texted you (07XX XXX XXX):",
    "invalid": "END That is not a Kenyan mobile number. Dial again and enter it like 0712 345 678.",
    "confirmed": "END WARNING: {n} is a confirmed scam number{cat}. Do not send money or share your PIN. "
                 "Forward scam SMS to 333.",
    "suspected": "END CAUTION: {n} has been reported for fraud by {k}. Do not send money until you are sure. "
                 "Forward scam SMS to 333.",
    "verified": "END Verified: {n} is {name}. Still, never share your PIN with anyone.",
    "unknown": "END No reports about {n} yet. That does not prove it is genuine: never share your PIN, "
               "and forward scam SMS to 333.",
    "tips": "END Safaricom never asks for your PIN. Money \"sent by mistake\"? Check your M-PESA balance first; "
            "only Safaricom reverses payments. Forward scam SMS to 333.",
    "busy": "END Too many checks from this phone. Please try again in an hour.",
}
SW = {
    "menu": "CON Linda: angalia namba kabla ya kulipa\n1. Angalia namba\n2. Jinsi ya kujilinda",
    "ask": "CON Weka namba iliyokupigia au kukutumia ujumbe (07XX XXX XXX):",
    "invalid": "END Hiyo si namba ya simu ya Kenya. Piga tena na uiweke kama 0712 345 678.",
    "confirmed": "END ONYO: {n} ni namba ya ulaghai iliyothibitishwa{cat}. Usitume pesa wala PIN yako. "
                 "Tuma SMS za ulaghai kwa 333.",
    "suspected": "END TAHADHARI: {n} imeripotiwa kwa ulaghai na {k}. Usitume pesa hadi uwe na uhakika. "
                 "Tuma SMS za ulaghai kwa 333.",
    "verified": "END Imethibitishwa: {n} ni {name}. Hata hivyo, usimpe mtu yeyote PIN yako.",
    "unknown": "END Hakuna ripoti kuhusu {n} bado. Hiyo haithibitishi kuwa ni halali: usitoe PIN yako, "
               "na tuma SMS za ulaghai kwa 333.",
    "tips": "END Safaricom haitakuuliza PIN yako. Umetumiwa pesa \"kimakosa\"? Angalia salio la M-PESA kwanza; "
            "Safaricom pekee hurudisha malipo. Tuma SMS za ulaghai kwa 333.",
    "busy": "END Umeangalia mara nyingi mno. Tafadhali jaribu tena baada ya saa moja.",
}
# The scam type in a few words: what kind of trick to expect, never the fraud team's evidence.
CATEGORY = {
    "fake_mpesa": ("fake M-PESA messages", "jumbe feki za M-PESA"),
    "sent_by_mistake": ("\"sent by mistake\" trick", "ujanja wa \"kimakosa\""),
    "prize": ("fake prizes", "zawadi feki"),
    "pin_request": ("asks for PINs", "huomba PIN"),
    "fuliza_upgrade": ("fake Fuliza offers", "ofa feki za Fuliza"),
    "kra_refund": ("fake KRA refunds", "marejesho feki ya KRA"),
    "job_fee": ("fake jobs", "kazi feki"),
    "loan_fee": ("fake loans", "mikopo feki"),
    "phishing_link": ("fake links", "viungo feki"),
}
_DIGITS = re.compile(r"[\s\-()]")


def to_msisdn(typed: str) -> str | None:
    """'0712 345 678', '+254712345678', '254712345678' or '712345678' -> '254712345678'; None if not a mobile number."""
    d = _DIGITS.sub("", typed).lstrip("+")
    if re.fullmatch(r"0[17]\d{8}", d):
        d = "254" + d[1:]
    elif re.fullmatch(r"[17]\d{8}", d):
        d = "254" + d
    return d if re.fullmatch(r"254[17]\d{8}", d) else None


def pretty(msisdn: str) -> str:
    """254700000050 -> 0700 000 050, the way people write numbers."""
    local = "0" + msisdn[3:]
    return f"{local[:4]} {local[4:7]} {local[7:]}"


def answer(app, phone: str, typed: str, words: dict, sw: bool) -> str:
    msisdn = to_msisdn(typed)
    if msisdn is None:
        return words["invalid"]
    if not app.state.lookup_limiter.allow("ussd:" + phone):
        return words["busy"]
    risk = risk_of(app, msisdn)
    n, status = pretty(msisdn), risk["status"]
    if status == "verified":
        return words["verified"].format(n=n, name=risk["name_sw" if sw else "name_en"])
    if status == "confirmed":
        cat = CATEGORY.get(risk.get("category") or "")
        return words["confirmed"].format(n=n, cat=f" ({cat[1] if sw else cat[0]})" if cat else "")
    if status == "suspected":
        k = max(risk["reports"], 1)
        people = f"watu {k}" if sw else ("1 person" if k == 1 else f"{k} people")
        return words["suspected"].format(n=n, k=people)
    return words["unknown"].format(n=n)


def reply(app, phone: str, text: str) -> str:
    parts = text.split("*") if text else []
    sw = bool(parts) and parts[0] == "3"
    if sw:
        parts = parts[1:]
    words = SW if sw else EN
    if not parts:
        return words["menu"]
    if parts[0] == "1":
        return words["ask"] if len(parts) == 1 else answer(app, phone, parts[1], words, sw)
    if parts[0] == "2":
        return words["tips"]
    return words["menu"]  # anything else: show the menu again


@router.post("/ussd", response_class=PlainTextResponse)
async def ussd(request: Request) -> str:
    # Read the form by hand (urllib), so the gateway needs no extra form-parsing library.
    form = {k: v[0] for k, v in parse_qs((await request.body()).decode("utf-8"), keep_blank_values=True).items()}
    return reply(request.app, form.get("phoneNumber", ""), form.get("text", "").strip())
