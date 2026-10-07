"""What the radar calls on the network (contract 5.4)."""
from fastapi import APIRouter, Query, Request
from pydantic import BaseModel

router = APIRouter()


class ConfirmIn(BaseModel):
    sender: str


@router.post("/network/confirm")
async def network_confirm(body: ConfirmIn, request: Request) -> dict:
    return {"warned_now": await request.app.state.engine.handle_confirm(body.sender)}


@router.get("/deliveries")
def deliveries(request: Request, sender: str, since: float = Query(default=0.0)) -> dict:
    """Who received messages from `sender` since a simulated time (seconds)."""
    return {"recipients": request.app.state.engine.recipients_since(sender, since)}
