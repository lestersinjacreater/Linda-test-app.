"""The simulation engine: a tiny discrete-event simulator.

Everything that "happens" (an SMS arrives, a phone detects it, a report is sent, a warning is
delivered) is an entry in a time-ordered queue. The engine pops entries in order of SIMULATED
time and, if SIM_SPEED > 0, sleeps so the demo plays out at a watchable speed. With SIM_SPEED=0
it runs instantly, which is what the tests use. Randomness is seeded, so a run is repeatable.

The engine talks to the radar only through the RadarClient (contract 5.1), and the radar talks
back only through POST /network/confirm (contract 5.4), which calls `handle_confirm` below.
"""
import asyncio
import heapq
import random
from datetime import datetime, timedelta, timezone
from typing import Any, Awaitable, Callable, Optional

from src.core.config import Settings
from src.core.events import EventBus
from src.features.engine.radar_client import RadarClient
from src.features.population.population import Phone, build_population
from src.vendor.linda.categories import categorise
from src.vendor.linda.normalize import normalize
from src.vendor.linda.scorer import Scorer
from src.vendor.linda.simhash import simhash64

BASE_TIME = datetime(2026, 10, 20, 14, 0, 0, tzinfo=timezone.utc)  # the "wall clock" of sim time 0
NAMES = ["JOHN KAMAU", "MARY ACHIENG", "PETER OTIENO", "JANE WANJIKU", "DAVID KIPROP", "GRACE NYAMBURA"]
CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ"


class Engine:
    def __init__(self, settings: Settings, radar: RadarClient, bus: EventBus):
        self.settings = settings
        self.radar = radar
        self.bus = bus
        self.scorer = Scorer.from_file(settings.model_path)
        self.run_number = 0
        self.running = False
        self._task: Optional[asyncio.Task] = None
        self._reset_state()

    # ------------------------------------------------------------------ state
    def _reset_state(self) -> None:
        self.sim_now = 0.0
        self.last_report_time = 0.0
        self._heap: list[tuple[float, int, Callable[[], Awaitable[None]]]] = []
        self._seq = 0
        self.phones: list[Phone] = []
        self.deliveries: dict[str, list[tuple[Phone, float]]] = {}   # sender -> (phone, delivered_at)
        self.warned: dict[str, dict[int, dict[str, Any]]] = {}       # sender -> phone id -> warning info
        self.confirmed_at: dict[str, float] = {}
        self.blast_start: dict[str, float] = {}
        self.first_detection: dict[str, float] = {}
        self._confirm_events: dict[str, asyncio.Event] = {}
        self.latest_sender: Optional[str] = None
        self.summary: dict[str, Any] = {}

    def emit(self, type_: str, **fields: Any) -> None:
        self.bus.publish({"type": type_, "sim_time": round(self.sim_now, 2), "run": self.run_number, **fields})

    def push(self, at: float, fn: Callable[[], Awaitable[None]]) -> None:
        self._seq += 1
        heapq.heappush(self._heap, (at, self._seq, fn))

    @staticmethod
    def _phone_fields(p: Phone) -> dict[str, Any]:
        return {"phone": p.id, "kind": p.kind, "town": p.town, "lat": p.lat, "lon": p.lon}

    # --------------------------------------------------------------- control
    async def reset(self) -> None:
        if self._task and not self._task.done():
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
        self.running = False
        self._reset_state()
        self.bus.clear()
        self.emit("reset")

    def start(self, scenario: str) -> dict[str, Any]:
        """Begin a scenario in the background. Returns at once with the run's identifiers."""
        if self.running:
            raise RuntimeError("a scenario is already running")
        self._reset_state()
        self.bus.clear()  # every scenario is its own act: the dashboard clears its map when it sees "reset"
        self.run_number += 1
        self.emit("reset")
        s = self.settings
        self.phones = build_population(s.sim_seed, self.run_number, s.linda_phones, s.smartphones, s.feature_phones, s.device_prefix)
        senders = [f"254700{900000 + 10 * self.run_number + k}" for k in range(3)]  # fresh numbers every run
        self.summary = {"scenario": scenario, "run": self.run_number, "senders": senders[: 3 if scenario == "rotating" else 1]}
        self.latest_sender = senders[0]
        self.running = True
        self._task = asyncio.create_task(self._run(scenario, senders))
        return {"run": self.run_number, "scenario": scenario, "senders": self.summary["senders"]}

    async def wait(self) -> None:
        if self._task:
            await self._task

    async def _run(self, scenario: str, senders: list[str]) -> None:
        try:
            rng = random.Random(self.settings.sim_seed)  # same seed, same demo, every run
            if scenario == "blast":
                self._schedule_blast(senders[0], rng, 0.0, self.settings.blast_recipients, "blast")
            elif scenario == "rotating":
                each = self.settings.blast_recipients // 3
                gap = self.settings.blast_duration_s + 60.0
                template = self._campaign_text(rng)  # one script, sent from three numbers in turn
                for k, number in enumerate(senders):
                    self._schedule_blast(number, rng, k * gap, each, "rotating", template=template)
                self.latest_sender = senders[-1]
            elif scenario == "poison":
                self._schedule_poison(senders[0], rng)
            await self._drain()
        finally:
            self.running = False

    async def _drain(self) -> None:
        speed = self.settings.sim_speed
        while self._heap:
            at, _, fn = heapq.heappop(self._heap)
            if speed > 0 and at > self.sim_now:
                await asyncio.sleep((at - self.sim_now) / speed)
            self.sim_now = max(self.sim_now, at)
            await fn()

    # ----------------------------------------------------------- the message
    @staticmethod
    def _campaign_text(rng: random.Random) -> str:
        """The scam script: a fake M-Pesa confirmation. {code} and {amt} vary per recipient."""
        return ("{code} Confirmed. You have received Ksh{amt}.00 from " + rng.choice(NAMES)
                + " on 20/10/26 at 2:05 PM. New M-PESA balance is Ksh{bal}.00. Please send back the amount, sent in error.")

    @staticmethod
    def _fill(template: str, rng: random.Random) -> str:
        code = rng.choice(CODE_ALPHABET) + "".join(rng.choice(CODE_ALPHABET + "0123456789") for _ in range(8)) + str(rng.randint(0, 9))
        amt = rng.choice([1500, 2000, 2500, 3000, 4500, 5000])
        return template.format(code=code, amt=f"{amt:,}", bal=f"{amt + rng.randint(200, 900):,}")

    # --------------------------------------------------------------- scenarios
    def _schedule_blast(self, sender: str, rng: random.Random, start: float, n: int, scenario: str,
                        template: Optional[str] = None) -> None:
        template = template or self._campaign_text(rng)
        recipients = rng.sample(self.phones, min(n, len(self.phones)))
        self.blast_start[sender] = start
        self.deliveries[sender] = []
        self.warned[sender] = {}
        step = self.settings.blast_duration_s / max(1, len(recipients))

        async def begin() -> None:
            self.emit("blast_started", sender=sender, scenario=scenario, recipients=len(recipients))

        async def finish() -> None:
            self.emit("blast_finished", sender=sender, scenario=scenario, metrics=self.metrics(sender))

        self.push(start, begin)
        for i, phone in enumerate(recipients):
            at = start + i * step + rng.uniform(0, step)
            text = self._fill(template, rng)
            self.push(at, self._deliver(sender, phone, text, rng.uniform(0.2, 1.5)))
        self.push(start + self.settings.blast_duration_s + 20.0, finish)  # warnings land within about 10 s of confirmation

    def _deliver(self, sender: str, phone: Phone, text: str, detect_latency: float) -> Callable[[], Awaitable[None]]:
        async def fn() -> None:
            t = self.sim_now
            self.deliveries[sender].append((phone, t))
            self.emit("sms_delivered", sender=sender, **self._phone_fields(phone))
            if sender in self.confirmed_at:  # the network already knows this number: warn on delivery
                self._warn(sender, phone, t, "network")
            if phone.kind == "linda":  # Linda phones always check on the device, server or no server
                self.push(t + detect_latency, self._detect(sender, phone, text))
        return fn

    def _detect(self, sender: str, phone: Phone, text: str) -> Callable[[], Awaitable[None]]:
        async def fn() -> None:
            score = self.scorer.score(text, sender)  # the REAL model, with the sender as an input
            level = self.scorer.level(score)
            if level == "SAFE":
                return
            category = categorise(text, sender, self.scorer.verified)
            t = self.sim_now
            earlier = self.warned[sender].get(phone.id)
            if earlier is None or t < earlier["t"]:  # whichever warning reached the user first is the one that counts
                self.warned[sender][phone.id] = {"t": t, "source": "linda", "before_read": t < self._read_at(sender, phone)}
            self.first_detection.setdefault(sender, t)
            self.emit("linda_detected", sender=sender, score=round(score, 4), level=level, category=category,
                      before_read=t < self._read_at(sender, phone), **self._phone_fields(phone))
            if level == "SCAM":  # only confident detections are reported (android/CLAUDE.integration.md)
                self.push(t + 0.2, self._report(sender, phone, text, score, category))
        return fn

    def _report(self, sender: str, phone: Phone, text: str, score: float, category: str) -> Callable[[], Awaitable[None]]:
        async def fn() -> None:
            self.last_report_time = self.sim_now
            payload = {  # contract 5.1. There is NO message text in here, only its fingerprint.
                "sender": sender, "category": category, "confidence": round(score, 4),
                "fingerprint": simhash64(normalize(text)), "model_version": self.scorer.version,
                "device_id": phone.device_id, "sent_at": (BASE_TIME + timedelta(seconds=self.sim_now)).strftime("%Y-%m-%dT%H:%M:%SZ"),
            }
            answer = await self.radar.report(payload)
            if answer is None:
                self.emit("report_failed", sender=sender, **self._phone_fields(phone))
                return
            self.emit("report_sent", sender=sender, status=answer["status"], devices=answer["devices"], **self._phone_fields(phone))
            if answer["status"] == "confirmed" and sender not in self.confirmed_at:
                event = self._confirm_events.setdefault(sender, asyncio.Event())
                try:  # the radar calls POST /network/confirm; wait for it so the run stays repeatable
                    await asyncio.wait_for(event.wait(), self.settings.confirm_wait_s)
                except asyncio.TimeoutError:
                    pass
        return fn

    def _schedule_poison(self, victim: str, rng: random.Random, devices: int = 20) -> None:
        """Attack: brand-new fake devices all report an innocent number. The radar must NOT confirm it."""
        self.deliveries[victim] = []
        self.warned[victim] = {}
        self.blast_start[victim] = 0.0
        self.summary["poison_statuses"] = []

        async def begin() -> None:
            self.emit("blast_started", sender=victim, scenario="poison", recipients=0, attack=True)

        self.push(0.0, begin)
        for i in range(devices):
            device_id = "9999" + format(rng.getrandbits(48), "012x")  # not the trusted demo prefix

            async def fn(device_id: str = device_id) -> None:
                self.last_report_time = self.sim_now
                payload = {"sender": victim, "category": "fake_mpesa", "confidence": 0.99, "fingerprint": "0123456789abcdef",
                           "model_version": self.scorer.version, "device_id": device_id,
                           "sent_at": (BASE_TIME + timedelta(seconds=self.sim_now)).strftime("%Y-%m-%dT%H:%M:%SZ")}
                answer = await self.radar.report(payload)
                if answer is None:
                    self.emit("report_failed", sender=victim, attack=True)
                    return
                self.summary["poison_statuses"].append(answer["status"])
                self.emit("report_sent", sender=victim, status=answer["status"], devices=answer["devices"], attack=True)
            self.push(i * 2.0, fn)

        async def finish() -> None:
            blocked = "confirmed" not in self.summary["poison_statuses"]
            self.summary["poison_blocked"] = blocked
            self.emit("blast_finished", sender=victim, scenario="poison", attack=True, poison_blocked=blocked)
        self.push(devices * 2.0 + 5.0, finish)

    # --------------------------------------------------- the network side (5.4)
    def _read_at(self, sender: str, phone: Phone) -> float:
        delivered = next(t for p, t in self.deliveries[sender] if p.id == phone.id)
        return delivered + phone.read_delay_s

    def _warn(self, sender: str, phone: Phone, at: float, source: str) -> None:
        if phone.id in self.warned.setdefault(sender, {}):
            return
        before_read = at < self._read_at(sender, phone)
        self.warned[sender][phone.id] = {"t": at, "source": source, "before_read": before_read}

        async def fn() -> None:
            self.emit("warning_delivered", sender=sender, before_read=before_read, source=source, **self._phone_fields(phone))
        self.push(at, fn)

    async def handle_confirm(self, sender: str) -> int:
        """Radar says `sender` is confirmed: warn everyone who received from it. Returns how many were warned now."""
        if sender in self.confirmed_at:
            return 0
        confirm_t = self.last_report_time + self.settings.network_latency_s
        self.confirmed_at[sender] = confirm_t
        self.emit("sender_confirmed", sender=sender, confirmed_after_s=round(confirm_t - self.blast_start.get(sender, 0.0), 2))
        warned_now = 0
        for phone, _ in list(self.deliveries.get(sender, [])):
            if phone.id in self.warned.get(sender, {}):
                continue
            latency = 1.0 + (phone.id % 7)  # 1 to 7 simulated seconds, depends only on the phone
            self._warn(sender, phone, max(confirm_t, self.sim_now) + latency, "network")
            warned_now += 1
        self._confirm_events.setdefault(sender, asyncio.Event()).set()
        return warned_now

    def recipients_since(self, sender: str, since: float) -> list[str]:
        return [p.msisdn for p, t in self.deliveries.get(sender, []) if t >= since]

    def population_view(self) -> list[dict[str, Any]]:
        """Every phone with its town and position, for the dashboard map. Same in every run (the seed fixes it)."""
        s = self.settings
        phones = self.phones or build_population(s.sim_seed, 0, s.linda_phones, s.smartphones, s.feature_phones, s.device_prefix)
        return [{"phone": p.id, "kind": p.kind, "town": p.town, "lat": p.lat, "lon": p.lon} for p in phones]

    # ------------------------------------------------------------------ metrics
    def metrics(self, sender: Optional[str] = None) -> dict[str, Any]:
        sender = sender or self.latest_sender
        delivered = self.deliveries.get(sender, [])
        warned = self.warned.get(sender, {})
        by_id = {p.id: p for p, _ in delivered}
        feature = [p for p in by_id.values() if p.kind == "feature"]

        def pct(count: int, total: int) -> Optional[float]:
            return round(100.0 * count / total, 1) if total else None

        start = self.blast_start.get(sender, 0.0)
        return {
            "sender": sender, "run": self.run_number,
            "delivered": len(delivered), "warned": len(warned),
            "warned_before_read_pct": pct(sum(1 for w in warned.values() if w["before_read"]), len(delivered)),
            "feature_phones_delivered": len(feature),
            "feature_phones_warned_before_read_pct": pct(
                sum(1 for p in feature if warned.get(p.id, {}).get("before_read")), len(feature)),
            "first_detection_after_s": round(self.first_detection[sender] - start, 2) if sender in self.first_detection else None,
            "confirmed_after_s": round(self.confirmed_at[sender] - start, 2) if sender in self.confirmed_at else None,
            "warned_by_linda": sum(1 for w in warned.values() if w["source"] == "linda"),
            "warned_by_network": sum(1 for w in warned.values() if w["source"] == "network"),
        }
