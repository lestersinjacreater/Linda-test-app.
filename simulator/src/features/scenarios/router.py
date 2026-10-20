"""Presenter controls and the dashboard feed: scenarios, reset, metrics, odds, and the /events WebSocket (5.5)."""
from functools import lru_cache
from typing import Optional

from fastapi import APIRouter, HTTPException, Query, Request, WebSocket, WebSocketDisconnect

from src.features.adoption.odds import odds as replay_odds

router = APIRouter()

# Percent of people running Linda. Omit it for the classic 300-phone demo (10% adoption, easy to see).
Adoption = Query(default=None, gt=0, le=100, description="percent of people running Linda, e.g. 0.5, 1 or 2")


async def _start(request: Request, scenario: str, wait: bool, adoption: Optional[float] = None,
                 caller: Optional[str] = None) -> dict:
    engine = request.app.state.engine
    try:
        info = engine.start(scenario, adoption=adoption, caller=caller)
    except RuntimeError as exc:
        raise HTTPException(409, str(exc))
    if wait:
        await engine.wait()
        info["metrics"] = engine.metrics(info["senders"][-1]) if scenario != "poison" else {"poison_blocked": engine.summary.get("poison_blocked")}
    return info


@router.post("/scenarios/blast")
async def blast(request: Request, wait: bool = False, adoption: Optional[float] = Adoption) -> dict:
    return await _start(request, "blast", wait, adoption)


@router.post("/scenarios/rotating")
async def rotating(request: Request, wait: bool = False, adoption: Optional[float] = Adoption) -> dict:
    return await _start(request, "rotating", wait, adoption)


@router.post("/scenarios/poison")
async def poison(request: Request, wait: bool = False) -> dict:
    return await _start(request, "poison", wait)


@router.post("/scenarios/call")
async def call(request: Request, wait: bool = False, adoption: Optional[float] = Adoption,
               caller: Optional[str] = Query(default=None, pattern=r"^254[17]\d{8}$",
                                             description="a number the gateway may already know; omit for SMS then calls")) -> dict:
    """The scammer texts, then phones people. With `caller`: calls only, from a number the gateway may know."""
    return await _start(request, "call", wait, adoption, caller)


@lru_cache(maxsize=32)
def _cached_odds(levels: tuple[float, ...], trials: int, recipients: int, min_reports: int, seed: int,
                 duration_s: float, latency_s: float) -> dict:
    return replay_odds(levels, trials, recipients, min_reports, seed, duration_s, latency_s)


@router.get("/adoption/odds")
def adoption_odds(request: Request, levels: str = "0.5,1,2", trials: int = Query(default=1000, ge=100, le=5000),
                  min_reports: int = Query(default=3, ge=1, le=20)) -> dict:
    """The same 500-person blast replayed `trials` times per adoption level (see features/adoption/odds.py)."""
    try:
        parsed = tuple(float(x) for x in levels.split(","))
    except ValueError:
        raise HTTPException(422, "levels must be numbers separated by commas, e.g. 0.5,1,2")
    if not 1 <= len(parsed) <= 6 or not all(0 < x <= 100 for x in parsed):
        raise HTTPException(422, "give 1 to 6 levels, each above 0 and at most 100")
    s = request.app.state.settings
    return _cached_odds(parsed, trials, s.adoption_recipients, min_reports, s.sim_seed, s.blast_duration_s, s.network_latency_s)


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
