# Linda model metrics

Model `2026.10.19-1` · thresholds warn 0.55 / scam 0.8

> **READ THIS FIRST.** Section 8 is the only section measured on **real messages the model never saw** (1214 rows).
> There is **no Kenyan test data yet**: the real test rows are Tanzanian Swahili (Bongoscam) and international English (Mendeley). Quote them as such, not as results on Kenyan scams.
> Sections 1 to 6 mix synthetic and real messages and show the pipeline works.

## 1. Validation set (wordings the model never saw)

- Precision 100.0% · Recall 99.2% · F1 99.6%
- Confusion matrix: TP 353 · FP 0 · FN 3 · TN 737
- **False-alarm rate on hard negatives:** 0.0% of 180 (target under 2%)
- **False-alarm rate on verified-sender messages (M-Pesa, banks, KPLC, KRA):** 0.0% of 140 (target 0%)

## 2. Held-out campaigns (train without the campaign, then try to catch it)

| Campaign | Detected |
|---|---|
| bongoscam | 65.6% |
| fake_mpesa | 74.4% |
| fuliza_upgrade | 100.0% |
| job_fee | 100.0% |
| kra_refund | 100.0% |
| loan_fee | 100.0% |
| mendeley_smishing | 37.2% |
| phishing_link | 100.0% |
| pin_request | 75.0% |
| prize | 100.0% |
| reversal | 65.0% |
| **average** | **83.4%** |

## 3. Per-category recall (validation)

| Category | Recall |
|---|---|
| bongoscam | 99.1% |
| fake_mpesa | 100.0% |
| fuliza_upgrade | 100.0% |
| job_fee | 100.0% |
| kra_refund | 100.0% |
| loan_fee | 100.0% |
| mendeley_smishing | 95.8% |
| phishing_link | 100.0% |
| pin_request | 100.0% |
| prize | 100.0% |
| reversal | 100.0% |

## 4. Obfuscation robustness (validation scams)

- Original: 99.2% · Disguised (leetspeak, spacing, emoji): 99.2%

## 5. Fairness sanity check by language (validation)

| Language | Messages | Scam recall | False-alarm rate |
|---|---|---|---|
| en | 751 | 98.6% | 0.0% |
| sheng | 60 | - | 0.0% |
| sw | 282 | 99.5% | 0.0% |

## 6. Hand-written shared test vectors (never trained on)

- SAFE messages wrongly warned: **0 of 27** 
- Scams caught: 31 of 31; missed: none
- CAUTION cases scoring in the caution band: 0 of 5 (the text model alone cannot judge these: they need the app's context signals such as unknown sender and not in contacts)
- Level agreement with expected labels: 88.9%

## 7. Size and speed

- Model file: 1070 KB, vocabulary 20000
- 0.27 ms per message (Python, dev machine). Phone latency: NOT MEASURED (needs the cheapest team phone)

## 8. REAL test set (real messages, never trained or tuned on)

| Source | Messages | Precision | Recall | F1 | False-alarm rate |
|---|---|---|---|---|---|
| bongoscam | 109 | 100.0% | 100.0% | 100.0% | 0.0% |
| mendeley_smishing | 1105 | 97.0% | 94.1% | 95.5% | 0.2% |
| all | 1214 | 97.6% | 95.3% | 96.4% | 0.2% |
