"""The simulated country: a few hundred phones spread over Kenyan towns.

Three kinds: `linda` (smartphone running Linda), `smart` (smartphone without Linda) and
`feature` (basic phone: it can only be protected by a network SMS). Everything is seeded.
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


def build_population(seed: int, run: int, linda: int, smart: int, feature: int, device_prefix: str) -> list[Phone]:
    """Same seed gives the same towns and reading habits. Device ids also depend on `run`, so every
    demo run uses fresh devices and the radar never carries trust over from a previous run."""
    rng = random.Random(seed)
    kinds = ["linda"] * linda + ["smart"] * smart + ["feature"] * feature
    rng.shuffle(kinds)
    towns = [t for t in TOWNS for _ in range(t[3])]
    phones = []
    for i, kind in enumerate(kinds):
        name, lat, lon, _ = rng.choice(towns)
        device_id = None
        if kind == "linda":
            digest = hashlib.sha1(f"{seed}-{run}-{i}".encode()).hexdigest()
            device_id = device_prefix + digest[: 16 - len(device_prefix)]
        phones.append(Phone(
            id=i, msisdn=f"254700{100000 + i}", kind=kind, town=name,
            lat=round(lat + rng.uniform(-0.12, 0.12), 4), lon=round(lon + rng.uniform(-0.12, 0.12), 4),
            device_id=device_id,
            read_delay_s=round(min(1800.0, rng.lognormvariate(math.log(90), 0.9)), 1),  # median 90 s
        ))
    return phones
