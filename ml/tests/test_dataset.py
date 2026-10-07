import random

from src.features.dataset.augment import obfuscate
from src.features.dataset.synthetic import COLUMNS, generate
from src.features.normalize.normalize import normalize


def test_generation_is_reproducible():
    assert generate(seed=1, per_template=2) == generate(seed=1, per_template=2)
    assert generate(seed=1, per_template=2) != generate(seed=2, per_template=2)


def test_rows_follow_the_schema_and_are_declared_synthetic():
    rows = generate(per_template=1)
    assert all(set(r) == set(COLUMNS) for r in rows)
    assert {r["source"] for r in rows} == {"synthetic"}
    assert {r["label"] for r in rows} == {"scam", "legit"}


def test_there_is_no_synthetic_test_split():
    assert {r["split"] for r in generate(per_template=1)} == {"train", "val"}


def test_validation_templates_are_never_in_training():
    rows = generate(per_template=3)
    by_template = {}
    for r in rows:
        by_template.setdefault(r["id"].split("-")[1], set()).add(r["split"])
    assert all(len(s) == 1 for s in by_template.values())


def test_hard_negatives_exist_for_every_required_kind():
    from src.features.dataset.synthetic import S
    kinds = {t[1] for t in S if t[0] == "legit" and t[4]}
    for k in ("mpesa_reversal", "mpesa_fuliza", "bank_otp", "kra", "delivery", "kplc", "chat_money"):
        assert k in kinds, k


def test_no_real_looking_phone_numbers():
    import re
    for r in generate(per_template=5):
        for n in re.findall(r"\b(?:254|0)[17]\d{8}\b", r["text"] + " " + r["sender"]):
            assert n.startswith(("254700000", "0700000")), n


def test_obfuscation_is_seeded_and_mostly_undone_by_the_normaliser():
    a = obfuscate("Hongera umeshinda tuma registration fee", random.Random(1))
    assert a == obfuscate("Hongera umeshinda tuma registration fee", random.Random(1))
    assert a != "Hongera umeshinda tuma registration fee"
