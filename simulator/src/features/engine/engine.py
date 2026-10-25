"""The simulation engine: a tiny discrete-event simulator.

Everything that "happens" (an SMS arrives, a phone detects it, a report is sent, a warning is
delivered) is an entry in a time-ordered queue. The engine pops entries in order of SIMULATED
time and, if SIM_SPEED > 0, sleeps so the demo plays out at a watchable speed. With SIM_SPEED=0
it runs instantly, which is what the tests use. Randomness is seeded, so a run is repeatable.

The engine talks to the radar only through the RadarClient (contracts 5.1 and 5.2), and the radar
talks back only through POST /network/confirm (contract 5.4), which calls `handle_confirm` below.

How people WITHOUT Linda get protected: once the radar confirms a sender, the network warns everyone
who received a message from it (an SMS works on any phone, including basic phones), and from then on
warns new recipients on delivery and anyone the sender calls, right after the call.
"""
import asyncio
import heapq
import random
import secrets
from datetime import datetime, timedelta, timezone
from typing import Any, Awaitable, Callable, Optional

from src.core.config import Settings
from src.core.events import EventBus
from src.features.engine.radar_client import RadarClient
from src.features.engine.timing import DETECT_LATENCY_S, REPORT_DELAY_S
from src.features.population.population import Phone, build_adoption_population, build_population, real_phones
from src.features.realphones.sms import NoSms, SmsSender, warning_text
from src.vendor.linda.categories import categorise
from src.vendor.linda.normalize import normalize
from src.vendor.linda.scorer import Scorer
from src.vendor.linda.simhash import simhash64

BASE_TIME = datetime(2026, 10, 20, 14, 0, 0, tzinfo=timezone.utc)  # the "wall clock" of sim time 0
NAMES = ["JOHN KAMAU", "MARY ACHIENG", "PETER OTIENO", "JANE WANJIKU", "DAVID KIPROP", "GRACE NYAMBURA"]
CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ"
RUN_SLOTS = 10_000  # blocks of 10 placeholder scammer numbers: 254700900000 to 254700999999


class Engine:
    def __init__(self, settings: Settings, radar: RadarClient, bus: EventBus, sms: Optional[SmsSender] = None):
        self.settings = settings
        self.radar = radar
        self.bus = bus
        self.sms = sms or NoSms()
        self.scorer = Scorer.from_file(settings.model_path)
        self.run_number = 0
        # Scammer numbers and device ids must be fresh in every run, also after the telco restarts: the gateway keeps
        # what it learned, so a restarted telco that reused run 1's number would find it already confirmed, and the
        # network would never be told again (no warning wave). A random start per boot keeps them apart.
        self.run_base = secrets.randbelow(RUN_SLOTS)
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
        self.adoption: Optional[float] = None                         # percent, or None for the classic demo
        self.calls: dict[str, dict[int, dict[str, Any]]] = {}         # sender -> phone id -> what happened on the call
        self.caller_status: dict[str, str] = {}                       # what the gateway said about a known caller
        self.real_sms: list[dict[str, Any]] = []                      # what happened to each real SMS
        self._sms_tasks: list[asyncio.Task] = []

    def _run_slot(self) -> int:
        """Which block of placeholder numbers (254700900000 to 254700999999) and device ids this run uses."""
        return (self.run_base + self.run_number) % RUN_SLOTS

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

    def start(self, scenario: str, adoption: Optional[float] = None, caller: Optional[str] = None) -> dict[str, Any]:
        """Begin a scenario in the background. Returns at once with the run's identifiers.

        `adoption` (percent of people running Linda, e.g. 0.5, 1, 2) switches to the bigger country;
        `caller` (call scenario only) is a number the gateway may already know, e.g. from the fraud team.
        """
        if self.running:
            raise RuntimeError("a scenario is already running")
        self._reset_state()
        self.bus.clear()  # every scenario is its own act: the dashboard clears its map when it sees "reset"
        self.run_number += 1
        self.emit("reset")
        s = self.settings
        self.adoption = adoption
        if adoption is None:
            self.phones = build_population(s.sim_seed, self._run_slot(), s.linda_phones, s.smartphones, s.feature_phones, s.device_prefix)
        else:
            self.phones = build_adoption_population(s.sim_seed, self._run_slot(), s.adoption_population, adoption,
                                                    s.feature_share, s.device_prefix)
        self.phones += real_phones(s.real_phones, first_id=len(self.phones))
        senders = [f"254700{900000 + 10 * self._run_slot() + k}" for k in range(3)]  # fresh numbers every run
        if caller:
            senders = [caller]
        self.summary = {"scenario": scenario, "run": self.run_number, "senders": senders[: 3 if scenario == "rotating" else 1],
                        "adoption_pct": adoption, "known_caller": bool(caller)}
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
                self._schedule_blast(senders[0], rng, 0.0, self._recipients(), "blast")
            elif scenario == "call" and self.summary.get("known_caller"):
                await self._schedule_known_caller(senders[0])
            elif scenario == "call":
                sent = self._schedule_blast(senders[0], rng, 0.0, self._recipients(), "call", finish=False)
                self._schedule_calls(senders[0], sent)
            elif scenario == "rotating":
                each = self._recipients() // 3
                gap = self.settings.blast_duration_s + 60.0
                template = self._campaign_text(rng)  # one script, sent from three numbers in turn
                for k, number in enumerate(senders):
                    self._schedule_blast(number, rng, k * gap, each, "rotating", template=template)
                self.latest_sender = senders[-1]
            elif scenario == "poison":
                self._schedule_poison(senders[0], rng)
            await self._drain()
            if self._sms_tasks:  # real SMS go out in the background; finish them before the run ends
                await asyncio.gather(*self._sms_tasks)
        finally:
            self.running = False

    def _recipients(self) -> int:
        return self.settings.adoption_recipients if self.adoption is not None else self.settings.blast_recipients

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
                        template: Optional[str] = None, finish: bool = True) -> list[tuple[Phone, float]]:
        """Schedules the deliveries and returns who gets the SMS and when."""
        template = template or self._campaign_text(rng)
        simulated = [p for p in self.phones if not p.real]
        recipients = rng.sample(simulated, min(n, len(simulated)))
        self.blast_start[sender] = start
        self.deliveries[sender] = []
        self.warned[sender] = {}
        step = self.settings.blast_duration_s / max(1, len(recipients))

        async def begin() -> None:
            self.emit("blast_started", sender=sender, scenario=scenario, recipients=len(recipients))

        async def finish_fn() -> None:
            self.emit("blast_finished", sender=sender, scenario=scenario, metrics=self.metrics(sender))

        self.push(start, begin)
        sent = []
        for i, phone in enumerate(recipients):
            at = start + i * step + rng.uniform(0, step)
            text = self._fill(template, rng)
            self.push(at, self._deliver(sender, phone, text, rng.uniform(*DETECT_LATENCY_S)))
            sent.append((phone, at))
        # The real phones on the table get it a quarter of the way in, without using `rng`, so the simulated
        # part of the run is identical with or without them. They never receive the scam text for real.
        for phone in (p for p in self.phones if p.real):
            at = start + 0.25 * self.settings.blast_duration_s
            self.push(at, self._deliver(sender, phone, "", 0.0))
        if finish:
            self.push(start + self.settings.blast_duration_s + 20.0, finish_fn)  # warnings land within about 10 s of confirmation
        return sent

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
                self.push(t + REPORT_DELAY_S, self._report(sender, phone, text, score, category))
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
        """The network's warning SMS about a sender's MESSAGE. One per person per sender."""
        if phone.id in self.warned.setdefault(sender, {}):
            return
        before_read = at < self._read_at(sender, phone)
        self.warned[sender][phone.id] = {"t": at, "source": source, "before_read": before_read}

        async def fn() -> None:
            self.emit("warning_delivered", sender=sender, before_read=before_read, source=source, channel="sms",
                      real_sms=phone.real, **self._phone_fields(phone))
            self._send_real_sms(phone, sender, "sms")
        self.push(at, fn)

    def _send_real_sms(self, phone: Phone, sender: str, channel: str) -> None:
        """Only for the real phones on the table, and only the warning (see features/realphones/sms.py)."""
        if not phone.real:
            return

        async def send() -> None:
            status = await self.sms.send(phone.msisdn, warning_text(sender, channel))
            self.real_sms.append({"phone": phone.id, "sender": sender, "channel": channel, "status": status})
        self._sms_tasks.append(asyncio.create_task(send()))

    # ------------------------------------------------------------ scam calls
    def _schedule_calls(self, sender: str, sent: list[tuple[Phone, float]]) -> None:
        """After the SMS, the scammer phones people: every second SMS recipient a little after their SMS
        ("did you get my money? send it back"), plus some people who never got the SMS. Uses its own random
        stream, so the SMS part of this scenario is exactly the blast scenario."""
        s = self.settings
        crng = random.Random(s.sim_seed + 1)
        self.calls[sender] = {}
        followed_up = crng.sample(sent, round(len(sent) * s.call_share))
        plan = [(phone, at + crng.uniform(*s.call_after_sms_s)) for phone, at in followed_up]
        texted = {p.id for p, _ in sent}
        strangers = [p for p in self.phones if not p.real and p.id not in texted]
        window = s.blast_duration_s + s.call_after_sms_s[1]
        plan += [(p, crng.uniform(0, window)) for p in crng.sample(strangers, min(s.cold_calls, len(strangers)))]
        self._push_calls(sender, plan, crng, "call")

    async def _schedule_known_caller(self, caller: str) -> None:
        """Calls only, from a number the gateway may already know (5.2): e.g. one the fraud team blocked."""
        s = self.settings
        answer = await self.radar.risk(caller)
        status = (answer or {}).get("status", "unknown")
        self.caller_status[caller] = status
        self.deliveries[caller], self.warned[caller], self.calls[caller] = [], {}, {}
        self.blast_start[caller] = 0.0
        if status == "confirmed":  # the network already knows: everyone is protected from the first call
            self.confirmed_at[caller] = 0.0
            self.emit("sender_confirmed", sender=caller, confirmed_after_s=0.0, by="gateway")
        crng = random.Random(s.sim_seed + 1)
        simulated = [p for p in self.phones if not p.real]
        n = min(len(simulated), round(self._recipients() * s.call_share) + s.cold_calls)
        plan = [(p, crng.uniform(0, s.blast_duration_s)) for p in crng.sample(simulated, n)]
        self._push_calls(caller, plan, crng, "call", known_status=status)

    def _push_calls(self, sender: str, plan: list[tuple[Phone, float]], crng: random.Random, scenario: str,
                    known_status: Optional[str] = None) -> None:
        if known_status is not None:
            async def begin() -> None:
                self.emit("blast_started", sender=sender, scenario=scenario, recipients=0, calls=len(plan), known=known_status)
            self.push(0.0, begin)
        last = 0.0
        for phone, at in sorted(plan, key=lambda x: x[1]):
            length = crng.uniform(*self.settings.call_length_s)
            self.push(at, self._call(sender, phone, length))
            last = max(last, at + length)

        async def finish() -> None:
            self.emit("blast_finished", sender=sender, scenario=scenario, metrics=self.metrics(sender))
        self.push(last + 10.0, finish)

    def _call(self, sender: str, phone: Phone, length: float) -> Callable[[], Awaitable[None]]:
        async def fn() -> None:
            t = self.sim_now
            confirmed = sender in self.confirmed_at and self.confirmed_at[sender] <= t
            self.emit("call_attempt", sender=sender, known=confirmed, **self._phone_fields(phone))
            info: dict[str, Any] = {"t": t, "kind": phone.kind, "protected_by": None}
            if phone.kind == "linda":
                # Same order as the app's CallRisk: its own recent scam message, then the gateway's answer.
                own = self.warned.get(sender, {}).get(phone.id, {}).get("source") == "linda"
                if own:
                    label = "recent_scam_message"
                elif confirmed:
                    label = "reported"
                elif self.caller_status.get(sender) in ("suspected", "reported"):
                    label = "suspected"
                else:
                    label = None
                if label:
                    info["protected_by"] = "label"
                    self.emit("call_labelled", sender=sender, label=label, before_answer=True, **self._phone_fields(phone))
            elif phone.id in self.warned.get(sender, {}):
                info["protected_by"] = "sms_warning"  # already warned about this number by the network's SMS
            elif confirmed:
                # A basic phone cannot show a label on the call, so the network texts right after it ends.
                info["protected_by"] = "after_call"
                at = t + length + 1.0 + (phone.id % 7)
                info["warned_at"] = at

                async def warn() -> None:
                    self.emit("warning_delivered", sender=sender, before_read=False, source="network", channel="after_call",
                              real_sms=phone.real, **self._phone_fields(phone))
                    self._send_real_sms(phone, sender, "after_call")
                self.push(at, warn)
            self.calls[sender][phone.id] = info
        return fn

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
        if not self.phones:
            classic = build_population(s.sim_seed, 0, s.linda_phones, s.smartphones, s.feature_phones, s.device_prefix)
            phones = classic + real_phones(s.real_phones, first_id=len(classic))
        else:
            phones = self.phones
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
            "adoption_pct": self.adoption,
            "linda_recipients": sum(1 for p in by_id.values() if p.kind == "linda"),
            "calls": self._call_metrics(sender),
            "real_phone_sms": [r for r in self.real_sms if r["sender"] == sender],
        }

    def _call_metrics(self, sender: str) -> Optional[dict[str, Any]]:
        calls = self.calls.get(sender)
        if calls is None:
            return None
        by = [c["protected_by"] for c in calls.values()]
        linda = [c for c in calls.values() if c["kind"] == "linda"]
        return {
            "placed": len(calls),
            "to_linda_phones": len(linda),
            "labelled_before_answer": sum(1 for c in linda if c["protected_by"] == "label"),
            "to_other_phones": len(calls) - len(linda),
            "already_warned_by_sms": by.count("sms_warning"),
            "warned_after_call": by.count("after_call"),
            "unprotected": by.count(None),
            "protected_pct": round(100.0 * (len(by) - by.count(None)) / len(by), 1) if by else None,
        }
