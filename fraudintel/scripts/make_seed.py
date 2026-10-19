"""Writes fraudintel/data/seed.json: what the mock fraud team "already knows" at the start of a demo.

Run from the repo root (only when the seed should change):  python -I fraudintel/scripts/make_seed.py

All phone numbers are fake placeholders (254700000xxx). Campaign fingerprints are SimHashes of many
variants of each scam script, computed with the same normaliser and SimHash the phones use (from ml/),
so a phone report of a new variant lands within a few bits of one of them. This script is a dev tool:
the running service only reads the JSON it writes and never imports ml/.
"""
import json
import random
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "ml"))

from src.features.fingerprint.simhash import simhash64  # noqa: E402
from src.features.normalize.normalize import normalize  # noqa: E402

NAMES = ["JOHN KAMAU", "MARY ACHIENG", "PETER OTIENO", "JANE WANJIKU", "ALICE MUTHONI", "DAVID KIPROP",
         "GRACE NJERI", "JAMES MWANGI"]
AMOUNTS = [1500, 2000, 2500, 3000, 4500, 5000]
LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"


def _code(rng):
    return rng.choice(LETTERS) + "".join(rng.choice(LETTERS + "0123456789") for _ in range(8)) + str(rng.randint(0, 9))


SCRIPTS = {
    "camp-fake-mpesa": ("fake_mpesa", lambda r: (
        f"{_code(r)} Confirmed. You have received Ksh{(a := r.choice(AMOUNTS)):,}.00 from {r.choice(NAMES)} "
        f"on 20/10/26 at 2:05 PM. New M-PESA balance is Ksh{a + r.randint(200, 900):,}.00. "
        "Please send back the amount, sent in error.")),
    "camp-sent-by-mistake": ("sent_by_mistake", lambda r: (
        f"Nimekutumia {r.choice(AMOUNTS):,} kimakosa, tafadhali nirudishie haraka kwa namba hii")),
    "camp-prize": ("prize", lambda r: (
        f"Hongera! Umeshinda Ksh {r.choice([20000, 50000, 100000]):,}. Tuma registration fee ya "
        f"Ksh {r.choice([300, 500, 1000]):,} upate zawadi yako.")),
}
VARIANTS = 24  # enough that a new variant is within 6 bits of one of them about 99% of the time


def main() -> None:
    rng = random.Random(42)
    campaigns = []
    for cid, (category, make) in SCRIPTS.items():
        fps = sorted({simhash64(normalize(make(rng))) for _ in range(VARIANTS)})
        campaigns.append({"id": cid, "category": category, "fingerprints": fps})
    numbers = [
        # The app's demo-mode scammer: blocked, so "Reported for fraud" works on a fresh install.
        {"msisdn": "254700000777", "status": "blocked", "category": "fake_mpesa", "reports": 41, "campaign_id": "camp-fake-mpesa"},
        {"msisdn": "254700000801", "status": "blocked", "category": "sent_by_mistake", "reports": 27, "campaign_id": "camp-sent-by-mistake"},
        {"msisdn": "254700000802", "status": "campaign", "category": "prize", "reports": 9, "campaign_id": "camp-prize"},
        {"msisdn": "254700000803", "status": "reported", "category": "pin_request", "reports": 2, "campaign_id": None},
        {"msisdn": "254700000804", "status": "reported", "category": "sent_by_mistake", "reports": 1, "campaign_id": None},
    ]
    out = ROOT / "fraudintel" / "data" / "seed.json"
    out.write_text(json.dumps({"note": "Fake placeholder numbers only. Regenerate with fraudintel/scripts/make_seed.py.",
                               "numbers": numbers, "campaigns": campaigns}, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {out}: {len(numbers)} numbers, {sum(len(c['fingerprints']) for c in campaigns)} fingerprints")


if __name__ == "__main__":
    main()
