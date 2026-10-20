"""The simulated country: a few hundred phones spread over Kenyan towns.

Three kinds: `linda` (smartphone running Linda), `smart` (smartphone without Linda) and
`feature` (basic phone: it can only be protected by a network SMS). Everything is seeded.

Two ways to build it:
- `build_population`: the classic 300-phone demo (30 Linda phones, 10% adoption: easy to see on the map).
- `build_adoption_population`: a bigger country where only a realistic share runs Linda (0.5%, 1%, 2%).
  The SAME seed always gives the same phones, towns, reading habits and kinds; only who has Linda
  changes, and the 0.5% users are also in the 1% group, who are also in the 2% group. So comparing
  adoption levels compares like with like.
"""
import hashlib
import math
import random
from dataclasses import dataclass

TOWNS = [  # name, latitude, longitude, weight
    ("Nairobi", -1.286, 36.817, 8), ("Mombasa", -4.043, 39.668, 3), ("Kisumu", -0.091, 34.768, 2),
    ("Nakuru", -0.303, 36.080, 2), ("Eldoret", 0.514, 35.270, 2), ("Nyeri", -0.420, 36.951, 1),
    ("Machakos", -1.517, 37.265, 1), ("Kakamega", 0.282, 34.752, 1), ("Garissa", -0.453, 39.646, 1),
    ("Thika", -1.033, 37.069, 2),
]


@dataclass(frozen=True)
class Phone:
    id: int
    msisdn: str          # 2547XXXXXXXX placeholder, never a real number
    kind: str            # linda | smart | feature
    town: str
    lat: float
    lon: float
    device_id: str | None  # only Linda phones have one
    read_delay_s: float  # how long after delivery the owner would open the message
    real: bool = False   # a real handset on the demo table (REAL_PHONES): warnings to it are sent as real SMS


REAL_PHONE_READ_DELAY_S = 300.0  # the presenter reads the real phone when the demo gets there


def read_delay(rng: random.Random) -> float:
    """How long after delivery someone opens a message: median 90 s, a few take up to 30 minutes."""
    return round(min(1800.0, rng.lognormvariate(math.log(90), 0.9)), 1)


def _device_id(device_prefix: str, seed: int, run: int, i: int) -> str:
    """Depends on `run`, so every demo run uses fresh devices and the radar never carries trust over."""
    digest = hashlib.sha1(f"{seed}-{run}-{i}".encode()).hexdigest()
    return device_prefix + digest[: 16 - len(device_prefix)]


def _spot(rng: random.Random, towns: list) -> tuple[str, float, float]:
    name, lat, lon, _ = rng.choice(towns)
    # about 50 km around the town: far enough apart to see on the map
    return name, round(lat + rng.uniform(-0.45, 0.45), 4), round(lon + rng.uniform(-0.45, 0.45), 4)


def real_phones(numbers: tuple[str, ...], first_id: int) -> list[Phone]:
    """The team's own basic phones on the demo table. They are basic phones in Nairobi (the venue)."""
    return [Phone(id=first_id + j, msisdn=m, kind="feature", town="Nairobi", lat=-1.286, lon=36.817,
                  device_id=None, read_delay_s=REAL_PHONE_READ_DELAY_S, real=True) for j, m in enumerate(numbers)]


def build_population(seed: int, run: int, linda: int, smart: int, feature: int, device_prefix: str) -> list[Phone]:
    """Same seed gives the same towns and reading habits. Device ids also depend on `run`, so every
    demo run uses fresh devices and the radar never carries trust over from a previous run."""
    rng = random.Random(seed)
    kinds = ["linda"] * linda + ["smart"] * smart + ["feature"] * feature
    rng.shuffle(kinds)
    towns = [t for t in TOWNS for _ in range(t[3])]
    phones = []
    for i, kind in enumerate(kinds):
        name, lat, lon = _spot(rng, towns)
        phones.append(Phone(
            id=i, msisdn=f"254700{100000 + i}", kind=kind, town=name, lat=lat, lon=lon,
            device_id=_device_id(device_prefix, seed, run, i) if kind == "linda" else None,
            read_delay_s=read_delay(rng),
        ))
    return phones


def build_adoption_population(seed: int, run: int, total: int, adoption_pct: float, feature_share: float,
                              device_prefix: str) -> list[Phone]:
    """`total` phones, of which `adoption_pct` percent of ALL people run Linda.

    Each phone gets, from the seed alone: a town, a reading habit, whether it is a basic phone, and a
    place in the queue to install Linda. Basic phones cannot run Linda, so only smartphones queue. The
    first `round(total * adoption_pct / 100)` smartphones in the queue are Linda phones. Raising the
    adoption only adds people at the back of the queue, so the lower levels are always inside the higher.
    """
    # Its own random stream: the blast's choice of recipients also starts from `seed`, and two processes
    # reading the same stream would be tied together (who gets the SMS would depend on who has Linda).
    rng = random.Random(f"population-{seed}")
    towns = [t for t in TOWNS for _ in range(t[3])]
    base = []
    for i in range(total):
        name, lat, lon = _spot(rng, towns)
        is_feature = rng.random() < feature_share
        queue_place = rng.random()
        base.append((i, name, lat, lon, is_feature, queue_place, read_delay(rng)))
    smartphones = sorted((b for b in base if not b[4]), key=lambda b: b[5])
    adopters = {b[0] for b in smartphones[: min(len(smartphones), round(total * adoption_pct / 100))]}
    phones = []
    for i, name, lat, lon, is_feature, _, delay in base:
        kind = "linda" if i in adopters else ("feature" if is_feature else "smart")
        phones.append(Phone(
            id=i, msisdn=f"254700{100000 + i}", kind=kind, town=name, lat=lat, lon=lon,
            device_id=_device_id(device_prefix, seed, run, i) if kind == "linda" else None,
            read_delay_s=delay,
        ))
    return phones
