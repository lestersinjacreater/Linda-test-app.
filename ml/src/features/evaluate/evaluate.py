"""Evaluates the model and writes ml/reports/metrics.md and metrics.json.

HONESTY RULES (ml/CLAUDE.md): never evaluate on rows the model trained on; `split == test` is
reserved for REAL data. There is no real data yet, so this report says so at the top and every
number below is from synthetic or hand-written messages. Do not quote them as results.

Run from the repo root:  python -I ml/src/features/evaluate/evaluate.py
"""
import json
import random
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "ml"))

from src.features.dataset.augment import obfuscate  # noqa: E402
from src.features.scoring.scorer import Scorer  # noqa: E402
from src.features.train import train as T  # noqa: E402

REPORTS = ROOT / "ml" / "reports"
VECTORS = ROOT / "shared" / "test-vectors.json"
WARN = T.THRESHOLDS["warn"]


def rate(num: int, den: int) -> float:
    return num / den if den else 0.0


def pct(x: float) -> str:
    return f"{100 * x:.1f}%"


def confusion(y_true: list[int], y_pred: list[int]) -> dict:
    tp = sum(1 for t, p in zip(y_true, y_pred) if t and p)
    fp = sum(1 for t, p in zip(y_true, y_pred) if not t and p)
    fn = sum(1 for t, p in zip(y_true, y_pred) if t and not p)
    tn = sum(1 for t, p in zip(y_true, y_pred) if not t and not p)
    precision, recall = rate(tp, tp + fp), rate(tp, tp + fn)
    f1 = rate(2 * precision * recall, precision + recall)
    return {"tp": tp, "fp": fp, "fn": fn, "tn": tn, "precision": precision, "recall": recall, "f1": f1}


def scores_for(vectorizer, clf, rows, verified):
    return list(T.probabilities(vectorizer, clf, rows, verified))


def main() -> None:
    verified = T.load_verified()
    rows = T.load_rows()
    train_rows = T.add_obfuscated_copies([r for r in rows if r["split"] == "train"])
    val_rows = [r for r in rows if r["split"] == "val"]
    real_test = [r for r in rows if r["split"] == "test"]
    C = T.choose_C(train_rows, val_rows, verified)
    vec, clf = T.train(train_rows, verified, C)
    m: dict = {"thresholds": T.THRESHOLDS, "model_version": T.MODEL_VERSION, "real_test_rows": len(real_test)}

    # 1. Validation set (synthetic, unseen templates)
    y = [1 if r["label"] == "scam" else 0 for r in val_rows]
    s = scores_for(vec, clf, val_rows, verified)
    pred = [1 if x >= WARN else 0 for x in s]
    m["validation"] = confusion(y, pred)
    hn = [i for i, r in enumerate(val_rows) if r["is_hard_negative"] == "true"]
    m["validation"]["hard_negative_false_alarm_rate"] = rate(sum(pred[i] for i in hn), len(hn))
    mp = [i for i, r in enumerate(val_rows) if r["campaign"] == "legit" and r["sender"].upper() in verified]
    m["validation"]["verified_sender_false_alarm_rate"] = rate(sum(pred[i] for i in mp), len(mp))
    m["validation"]["hard_negatives"] = len(hn)
    m["validation"]["verified_sender_messages"] = len(mp)

    # 2. Per-category recall (validation scams)
    per_cat = {}
    for c in sorted({r["campaign"] for r in val_rows if r["label"] == "scam"}):
        idx = [i for i, r in enumerate(val_rows) if r["campaign"] == c]
        per_cat[c] = rate(sum(pred[i] for i in idx), len(idx))
    m["per_category_recall"] = per_cat

    # 3. Held-out campaign: train WITHOUT the campaign, then try to catch it
    held = {}
    for c in sorted({r["campaign"] for r in rows if r["label"] == "scam"}):
        sub_train = [r for r in train_rows if r["campaign"] != c]
        v2, c2 = T.train(sub_train, verified, C)
        target = [r for r in rows if r["campaign"] == c]  # every message of the unseen campaign
        sc = scores_for(v2, c2, target, verified)
        held[c] = rate(sum(1 for x in sc if x >= WARN), len(target))
    m["held_out_campaign_detection"] = held
    m["held_out_campaign_average"] = sum(held.values()) / len(held)

    # 4. Obfuscation robustness (validation scams, original vs disguised with a different seed)
    rng = random.Random(7)
    scam_val = [r for r in val_rows if r["label"] == "scam"]
    disguised = [{**r, "text": obfuscate(r["text"], rng)} for r in scam_val]
    orig = rate(sum(1 for x in scores_for(vec, clf, scam_val, verified) if x >= WARN), len(scam_val))
    obf = rate(sum(1 for x in scores_for(vec, clf, disguised, verified) if x >= WARN), len(disguised))
    m["obfuscation"] = {"original_detection": orig, "disguised_detection": obf}

    # 5. Fairness sanity: error rates by language (validation)
    fair = {}
    for lang in sorted({r["language"] for r in val_rows}):
        idx = [i for i, r in enumerate(val_rows) if r["language"] == lang]
        sc_idx = [i for i in idx if y[i]]
        lg_idx = [i for i in idx if not y[i]]
        fair[lang] = {"scam_recall": rate(sum(pred[i] for i in sc_idx), len(sc_idx)) if sc_idx else None,
                      "false_alarm_rate": rate(sum(pred[i] for i in lg_idx), len(lg_idx)) if lg_idx else None,
                      "n": len(idx)}
    m["by_language"] = fair

    # 6. Hand-written shared vectors (never trained on). Uses the exported-format scorer.
    model = T.to_artifact(vec, clf, verified, C)
    scorer = Scorer(model)
    vecs = json.loads(VECTORS.read_text(encoding="utf-8"))["vectors"]
    safe = [v for v in vecs if v["expected_label"] == "SAFE"]
    scams = [v for v in vecs if v["expected_label"] == "SCAM"]
    caution = [v for v in vecs if v["expected_label"] == "CAUTION"]
    sc = {v["id"]: scorer.score(v["text"], v["sender"]) for v in vecs}
    m["shared_vectors"] = {
        "safe_total": len(safe), "safe_false_alarms": sum(1 for v in safe if sc[v["id"]] >= WARN),
        "false_alarm_ids": [v["id"] for v in safe if sc[v["id"]] >= WARN],
        "scam_total": len(scams), "scam_caught": sum(1 for v in scams if sc[v["id"]] >= WARN),
        "missed_scam_ids": [v["id"] for v in scams if sc[v["id"]] < WARN],
        "caution_total": len(caution), "caution_in_band": sum(1 for v in caution if WARN <= sc[v["id"]] < T.THRESHOLDS["scam"]),
        "level_agreement": sum(1 for v in vecs if scorer.level(sc[v["id"]]) == v["expected_label"]) / len(vecs),
    }

    # 7. Size and speed (on THIS machine, not a phone)
    size = len(json.dumps(model).encode("utf-8"))
    t0 = time.perf_counter()
    for v in vecs:
        scorer.score(v["text"], v["sender"])
    ms = 1000 * (time.perf_counter() - t0) / len(vecs)
    m["size_and_speed"] = {"model_json_kb": round(size / 1024), "ms_per_message_python_dev_machine": round(ms, 2),
                           "vocabulary": len(vec.vocabulary_), "phone_latency": "NOT MEASURED (needs the cheapest team phone)"}

    # 8. Real test set, once it exists
    if real_test:
        yt = [1 if r["label"] == "scam" else 0 for r in real_test]
        st = scores_for(vec, clf, real_test, verified)
        m["real_test"] = confusion(yt, [1 if x >= WARN else 0 for x in st])

    REPORTS.mkdir(parents=True, exist_ok=True)
    (REPORTS / "metrics.json").write_text(json.dumps(m, indent=2), encoding="utf-8")
    (REPORTS / "metrics.md").write_text(render(m), encoding="utf-8")
    print((REPORTS / "metrics.md").read_text(encoding="utf-8"))


def render(m: dict) -> str:
    v, sv = m["validation"], m["shared_vectors"]
    lines = [
        "# Linda model metrics", "",
        f"Model `{m['model_version']}` · thresholds warn {m['thresholds']['warn']} / scam {m['thresholds']['scam']}", "",
        "> **READ THIS FIRST.** There is **no real test data yet** (real test rows: " + str(m["real_test_rows"]) + ").",
        "> Every number below comes from synthetic template messages or the hand-written shared vectors.",
        "> They prove the pipeline works. They are **not** evidence the model works on real scams. Do not quote them to judges as results.", "",
        "## 1. Validation set (synthetic, wordings the model never saw)", "",
        f"- Precision {pct(v['precision'])} · Recall {pct(v['recall'])} · F1 {pct(v['f1'])}",
        f"- Confusion matrix: TP {v['tp']} · FP {v['fp']} · FN {v['fn']} · TN {v['tn']}",
        f"- **False-alarm rate on hard negatives:** {pct(v['hard_negative_false_alarm_rate'])} of {v['hard_negatives']} (target under 2%)",
        f"- **False-alarm rate on verified-sender messages (M-Pesa, banks, KPLC, KRA):** {pct(v['verified_sender_false_alarm_rate'])} of {v['verified_sender_messages']} (target 0%)", "",
        "## 2. Held-out campaigns (train without the campaign, then try to catch it)", "",
        "| Campaign | Detected |", "|---|---|",
        *[f"| {c} | {pct(r)} |" for c, r in m["held_out_campaign_detection"].items()],
        f"| **average** | **{pct(m['held_out_campaign_average'])}** |", "",
        "## 3. Per-category recall (validation)", "",
        "| Category | Recall |", "|---|---|", *[f"| {c} | {pct(r)} |" for c, r in m["per_category_recall"].items()], "",
        "## 4. Obfuscation robustness (validation scams)", "",
        f"- Original: {pct(m['obfuscation']['original_detection'])} · Disguised (leetspeak, spacing, emoji): {pct(m['obfuscation']['disguised_detection'])}", "",
        "## 5. Fairness sanity check by language (validation)", "",
        "| Language | Messages | Scam recall | False-alarm rate |", "|---|---|---|---|",
        *[f"| {k} | {d['n']} | {pct(d['scam_recall']) if d['scam_recall'] is not None else '-'} | {pct(d['false_alarm_rate']) if d['false_alarm_rate'] is not None else '-'} |" for k, d in m["by_language"].items()], "",
        "## 6. Hand-written shared test vectors (never trained on)", "",
        f"- SAFE messages wrongly warned: **{sv['safe_false_alarms']} of {sv['safe_total']}** {sv['false_alarm_ids'] or ''}",
        f"- Scams caught: {sv['scam_caught']} of {sv['scam_total']}; missed: {sv['missed_scam_ids'] or 'none'}",
        f"- CAUTION cases scoring in the caution band: {sv['caution_in_band']} of {sv['caution_total']} "
        "(the text model alone cannot judge these: they need the app's context signals such as unknown sender and not in contacts)",
        f"- Level agreement with expected labels: {pct(sv['level_agreement'])}", "",
        "## 7. Size and speed", "",
        f"- Model file: {m['size_and_speed']['model_json_kb']} KB, vocabulary {m['size_and_speed']['vocabulary']}",
        f"- {m['size_and_speed']['ms_per_message_python_dev_machine']} ms per message (Python, dev machine). Phone latency: {m['size_and_speed']['phone_latency']}", "",
    ]
    if "real_test" in m:
        r = m["real_test"]
        lines += ["## 8. REAL test set", "", f"- Precision {pct(r['precision'])} · Recall {pct(r['recall'])} · F1 {pct(r['f1'])}", ""]
    return "\n".join(lines)


if __name__ == "__main__":
    main()
