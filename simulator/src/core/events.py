"""Event bus: every simulator event goes to a history list and to every connected WebSocket (contract 5.5)."""
import asyncio
from typing import Any


class EventBus:
    def __init__(self, history_limit: int = 5000):
        self.history: list[dict[str, Any]] = []
        self.subscribers: set[asyncio.Queue] = set()
        self._limit = history_limit

    def publish(self, event: dict[str, Any]) -> None:
        self.history.append(event)
        if len(self.history) > self._limit:
            del self.history[: len(self.history) - self._limit]
        for q in list(self.subscribers):
            q.put_nowait(event)

    def clear(self) -> None:
        self.history.clear()

    def subscribe(self) -> asyncio.Queue:
        q: asyncio.Queue = asyncio.Queue()
        self.subscribers.add(q)
        return q

    def unsubscribe(self, q: asyncio.Queue) -> None:
        self.subscribers.discard(q)
