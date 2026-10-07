"""Presenter controls and the dashboard feed: scenarios, reset, metrics, and the /events WebSocket (5.5)."""
from typing import Optional

from fastapi import APIRouter, HTTPException, Request, WebSocket, WebSocketDisconnect

router = APIRouter()


async def _start(request: Request, scenario: str, wait: bool) -> dict:
    engine = request.app.state.engine
    try:
        info = engine.start(scenario)
    except RuntimeError as exc:
        raise HTTPException(409, str(exc))
    if wait:
        await engine.wait()
        info["metrics"] = engine.metrics(info["senders"][-1]) if scenario != "poison" else {"poison_blocked": engine.summary.get("poison_blocked")}
    return info


@router.post("/scenarios/blast")
async def blast(request: Request, wait: bool = False) -> dict:
    return await _start(request, "blast", wait)


@router.post("/scenarios/rotating")
async def rotating(request: Request, wait: bool = False) -> dict:
    return await _start(request, "rotating", wait)


@router.post("/scenarios/poison")
async def poison(request: Request, wait: bool = False) -> dict:
    return await _start(request, "poison", wait)


@router.post("/scenarios/call")
async def call() -> dict:
    raise HTTPException(501, "call scenario arrives with call screening (P1)")


@router.post("/reset")
async def reset(request: Request) -> dict:
    await request.app.state.engine.reset()
    return {"ok": True}


@router.get("/metrics")
def metrics(request: Request, sender: Optional[str] = None) -> dict:
    return request.app.state.engine.metrics(sender)


@router.get("/population")
def population(request: Request) -> list:
    """The phones on the map (id, kind, town, lat, lon). Lets the dashboard draw idle phones before anything happens."""
    return request.app.state.engine.population_view()


@router.get("/history")
def history(request: Request) -> list:
    """Every event of the current run, in order. Used to record the dashboard's offline replay."""
    return list(request.app.state.bus.history)


@router.websocket("/events")
async def events(ws: WebSocket) -> None:
    bus = ws.app.state.bus
    await ws.accept()
    queue = bus.subscribe()
    try:
        for event in list(bus.history)[-500:]:  # catch a late dashboard up
            await ws.send_json(event)
        while True:
            await ws.send_json(await queue.get())
    except WebSocketDisconnect:
        pass
    finally:
        bus.unsubscribe(queue)
