# Linda model metrics

Model `2026.10.21-1` · thresholds warn 0.55 / scam 0.8

> **READ THIS FIRST.** Section 8 is the only section measured on **real messages the model never saw** (1331 rows).
> It includes real Kenyan messages (kenya_sms: the Mwatsefu/IwazoLab public set, labels reviewed by Claude and spot-checked by the team; generated-looking messages are kept out of the test).
> Sections 1 to 6 mix synthetic and real messages and show the pipeline works.

## 1. Validation set (wordings the model never saw)

- Precision 98.7% · Recall 98.1% · F1 98.4%
- Confusion matrix: TP 369 · FP 5 · FN 7 · TN 765
- **False-alarm rate on hard negatives:** 2.6% of 190 (target under 2%)
- **False-alarm rate on verified-sender messages (M-Pesa, banks, KPLC, KRA):** 0.0% of 140 (target 0%)

## 2. Held-out campaigns (train without the campaign, then try to catch it)

| Campaign | Detected |
|---|---|
| bongoscam | 77.9% |
| fake_mpesa | 100.0% |
| fuliza_upgrade | 100.0% |
| job_fee | 95.2% |
| kra_refund | 100.0% |
| loan_fee | 95.8% |
| mendeley_smishing | 73.1% |
| other | 29.3% |
| phishing_link | 89.0% |
| pin_request | 100.0% |
| prize | 99.0% |
| reversal | 54.0% |
| sent_by_mistake | 85.7% |
| **average** | **84.5%** |

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
| other | 75.0% |
| phishing_link | 100.0% |
| pin_request | 100.0% |
| prize | 100.0% |
| reversal | 100.0% |

## 4. Obfuscation robustness (validation scams)

- Original: 98.1% · Disguised (leetspeak, spacing, emoji): 97.6%

## 5. Fairness sanity check by language (validation)

| Language | Messages | Scam recall | False-alarm rate |
|---|---|---|---|
| en | 786 | 96.9% | 0.5% |
| mixed | 11 | 100.0% | 16.7% |
| sheng | 60 | - | 0.0% |
| sw | 289 | 99.0% | 1.3% |

## 6. Hand-written shared test vectors (never trained on)

- SAFE messages wrongly warned: **0 of 27** 
- Scams caught: 31 of 31; missed: none
- CAUTION cases scoring in the caution band: 0 of 5 (the text model alone cannot judge these: they need the app's context signals such as unknown sender and not in contacts)
- Level agreement with expected labels: 88.9%

## 7. Size and speed

- Model file: 86 KB, 32768 hashed slots, 1-byte weights
- 0.35 ms per message (Python, dev machine). Phone latency: NOT MEASURED (needs the cheapest team phone)

## 8. REAL test set (real messages, never trained or tuned on)

| Source | Messages | Precision | Recall | F1 | False-alarm rate |
|---|---|---|---|---|---|
| bongoscam | 109 | 100.0% | 100.0% | 100.0% | 0.0% |
| kenya_sms | 117 | 66.7% | 82.4% | 73.7% | 31.8% |
| mendeley_smishing | 1105 | 97.0% | 94.1% | 95.5% | 0.2% |
| all | 1331 | 84.2% | 90.4% | 87.2% | 1.9% |

Betting adverts in the test (counted as not-a-scam above): Linda's text model warns on **7 of 21**. In the app, adverts from a registered sender name are not warned on unless they carry a link or another scam sign, so this is the worst case.
