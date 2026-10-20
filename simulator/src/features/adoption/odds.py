"""How likely is the network to protect people at 0.5%, 1% and 2% Linda adoption?

One demo run is one roll of the dice: whether 0, 3 or 8 of the 500 recipients happen to run Linda
depends on the seed. So this replays the same blast many times, each time with different random
adopters, and reports what happens on average. It uses the engine's own timing (spread of the
blast, reading habits, detection and network delays) and the gateway's rule as a single number:
the network acts once `min_reports` Linda phones have reported the sender.

Assumption, stated wherever these numbers are shown: every Linda phone that receives this scam
catches it. True for the demo campaign (the real model scores it about 0.998), not for every scam.
"""
import random
import statistics
from math import comb

from src.features.engine.timing import DETECT_LATENCY_S, REPORT_DELAY_S, WARN_LATENCY_S
from src.features.population.population import read_delay


def chance_of_at_least(k: int, n: int, p: float) -> float:
    """Binomial: the chance that at least k of n recipients run Linda when each does with probability p."""
    return 1.0 - sum(comb(n, i) * p**i * (1 - p) ** (n - i) for i in range(k))


def one_blast(rng: random.Random, recipients: int, p: float, min_reports: int, duration_s: float,
              network_latency_s: float) -> dict:
    step = duration_s / recipients
    people = []  # (delivered_at, read_at, linda)
    for i in range(recipients):
        at = i * step + rng.uniform(0, step)
        people.append((at, at + read_delay(rng), rng.random() < p))
    detections = {i: at + rng.uniform(*DETECT_LATENCY_S) for i, (at, _, linda) in enumerate(people) if linda}
    reports = sorted(t + REPORT_DELAY_S for t in detections.values())
    confirm = reports[min_reports - 1] + network_latency_s if len(reports) >= min_reports else None

    by_linda = by_network = 0
    for i, (at, read_at, linda) in enumerate(people):
        if linda:
            by_linda += detections[i] < read_at
        elif confirm is not None:
            # delivered after confirmation: warned on delivery; before it: when the network acts
            warn_at = at if at >= confirm else confirm + rng.uniform(*WARN_LATENCY_S)
            by_network += warn_at < read_at
    return {"linda": len(detections), "confirmed_after_s": confirm,
            "by_linda": by_linda, "by_network": by_network}


def odds(levels: tuple[float, ...], trials: int, recipients: int, min_reports: int, seed: int,
         duration_s: float, network_latency_s: float) -> dict:
    out = []
    for pct in levels:
        rng = random.Random(f"{seed}-{pct}")  # same answer every time for the same question
        runs = [one_blast(rng, recipients, pct / 100, min_reports, duration_s, network_latency_s) for _ in range(trials)]
        confirmed = [r["confirmed_after_s"] for r in runs if r["confirmed_after_s"] is not None]
        share = 100.0 / recipients
        out.append({
            "adoption_pct": pct,
            "linda_recipients_avg": round(statistics.mean(r["linda"] for r in runs), 2),
            "confirmed_pct": round(100.0 * len(confirmed) / trials, 1),
            "confirmed_pct_binomial": round(100.0 * chance_of_at_least(min_reports, recipients, pct / 100), 1),
            "confirmed_after_s_median": round(statistics.median(confirmed), 1) if confirmed else None,
            "warned_before_read_pct": round(statistics.mean((r["by_linda"] + r["by_network"]) * share for r in runs), 1),
            "warned_before_read_pct_linda_only": round(statistics.mean(r["by_linda"] * share for r in runs), 1),
        })
    return {
        "recipients": recipients, "trials": trials, "min_reports": min_reports, "levels": out,
        "assumes": "every Linda phone that receives the scam catches it (true for the demo campaign, "
                   "which the model scores about 0.998); the network acts once min_reports phones have reported",
    }
