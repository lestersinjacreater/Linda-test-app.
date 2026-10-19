"""GET /v1/verified-callers (root CLAUDE.md 5.13): the whole list, for the phone's offline copy."""
from fastapi import APIRouter, Request

router = APIRouter()


@router.get("/v1/verified-callers")
def verified_callers(request: Request) -> dict:
    app = request.app
    return {
        "version": app.state.verified_callers_version,
        "callers": [
            {"msisdn": c.msisdn, "name_en": c.name_en, "name_sw": c.name_sw, "kind": c.kind}
            for c in app.state.verified_callers.values()
        ],
    }
