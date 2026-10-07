# Linda model metrics

Model `2026.10.07-1` · thresholds warn 0.55 / scam 0.8

> **READ THIS FIRST.** There is **no real test data yet** (real test rows: 0).
> Every number below comes from synthetic template messages or the hand-written shared vectors.
> They prove the pipeline works. They are **not** evidence the model works on real scams. Do not quote them to judges as results.

## 1. Validation set (synthetic, wordings the model never saw)

- Precision 100.0% · Recall 91.0% · F1 95.3%
- Confusion matrix: TP 182 · FP 0 · FN 18 · TN 220
- **False-alarm rate on hard negatives:** 0.0% of 180 (target under 2%)
- **False-alarm rate on verified-sender messages (M-Pesa, banks, KPLC, KRA):** 0.0% of 120 (target 0%)

## 2. Held-out campaigns (train without the campaign, then try to catch it)

| Campaign | Detected |
|---|---|
| fake_mpesa | 41.2% |
| fuliza_upgrade | 100.0% |
| job_fee | 70.0% |
| kra_refund | 75.0% |
| loan_fee | 100.0% |
| phishing_link | 63.7% |
| pin_request | 75.0% |
| prize | 100.0% |
| reversal | 50.0% |
| **average** | **75.0%** |

## 3. Per-category recall (validation)

| Category | Recall |
|---|---|
| fake_mpesa | 100.0% |
| fuliza_upgrade | 100.0% |
| job_fee | 10.0% |
| kra_refund | 100.0% |
| loan_fee | 100.0% |
| phishing_link | 100.0% |
| pin_request | 100.0% |
| prize | 100.0% |
| reversal | 100.0% |

## 4. Obfuscation robustness (validation scams)

- Original: 91.0% · Disguised (leetspeak, spacing, emoji): 88.5%

## 5. Fairness sanity check by language (validation)

| Language | Messages | Scam recall | False-alarm rate |
|---|---|---|---|
| en | 240 | 100.0% | 0.0% |
| sheng | 60 | - | 0.0% |
| sw | 120 | 82.0% | 0.0% |

## 6. Hand-written shared test vectors (never trained on)

- SAFE messages wrongly warned: **0 of 27** 
- Scams caught: 30 of 31; missed: ['job-fee-sw-01']
- CAUTION cases scoring in the caution band: 1 of 5 (the text model alone cannot judge these: they need the app's context signals such as unknown sender and not in contacts)
- Level agreement with expected labels: 92.1%

## 7. Size and speed

- Model file: 389 KB, vocabulary 7351
- 0.26 ms per message (Python, dev machine). Phone latency: NOT MEASURED (needs the cheapest team phone)
