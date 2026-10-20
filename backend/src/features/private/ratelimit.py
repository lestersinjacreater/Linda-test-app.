"""Per-device rate limit for lookups, so nobody can sweep every prefix to rebuild the list.

In memory and per gateway process: enough for the hackathon. Production would use a shared store.
The key is the app's anonymous device id (header X-Linda-Device), or the caller's address without one.
"""
import time
from collections import defaultdict, deque
from typing import Callable


class RateLimiter:
    def __init__(self, per_hour: int, clock: Callable[[], float] = time.time):
        self.per_hour, self.clock = per_hour, clock
        self.hits: dict[str, deque] = defaultdict(deque)

    def allow(self, key: str) -> bool:
        now = self.clock()
        q = self.hits[key]
        while q and q[0] <= now - 3600:
            q.popleft()
        if len(q) >= self.per_hour:
            return False
        q.append(now)
        return True
