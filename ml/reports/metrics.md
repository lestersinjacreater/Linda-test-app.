# Linda model metrics

Model `2026.10.26-1` · thresholds warn 0.55 / scam 0.8

> **READ THIS FIRST.** Section 8 is the only section measured on **real messages the model never saw** (2127 rows).
> It includes real Kenyan messages (kenya_sms: the Mwatsefu/IwazoLab public set, labels reviewed by Claude and spot-checked by the team; generated-looking messages are kept out of the test).
> Sections 1 to 6 mix synthetic and real messages and show the pipeline works.

## 1. Validation set (wordings the model never saw)

- Precision 97.2% · Recall 90.9% · F1 94.0%
- Confusion matrix: TP 350 · FP 10 · FN 35 · TN 1038
- **False-alarm rate on hard negatives:** 1.8% of 327 (target under 2%)
- **False-alarm rate on verified-sender messages (M-Pesa, banks, KPLC, KRA):** 0.0% of 235 (target 0%)

## 2. Held-out campaigns (train without the campaign, then try to catch it)

| Campaign | Detected |
|---|---|
| bongoscam | 36.8% |
| fake_mpesa | 23.2% |
| fuliza_upgrade | 98.8% |
| job_fee | 82.7% |
| kra_refund | 100.0% |
| loan_fee | 77.3% |
| mendeley_smishing | 35.5% |
| other | 25.4% |
| phishing_link | 68.5% |
| pin_request | 98.9% |
| prize | 99.0% |
| reversal | 55.5% |
| sent_by_mistake | 91.7% |
| **average** | **68.7%** |

## 3. Per-category recall (validation)

| Category | Recall |
|---|---|
| bongoscam | 88.9% |
| fake_mpesa | 100.0% |
| fuliza_upgrade | 100.0% |
| job_fee | 95.5% |
| kra_refund | 100.0% |
| loan_fee | 95.2% |
| mendeley_smishing | 75.0% |
| other | 68.2% |
| phishing_link | 91.3% |
| pin_request | 100.0% |
| prize | 100.0% |
| reversal | 100.0% |

## 4. Obfuscation robustness (validation scams)

- Original: 90.9% · Disguised (leetspeak, spacing, emoji): 90.1%

## 5. Fairness sanity check by language (validation)

| Language | Messages | Scam recall | False-alarm rate |
|---|---|---|---|
| en | 1018 | 88.4% | 0.9% |
| mixed | 40 | 100.0% | 3.1% |
| sheng | 60 | - | 0.0% |
| sw | 315 | 92.5% | 1.0% |

## 6. Hand-written shared test vectors (never trained on)

- SAFE messages wrongly warned: **0 of 27** 
- Scams caught: 31 of 31; missed: none
- CAUTION cases scoring in the caution band: 0 of 5 (the text model alone cannot judge these: they need the app's context signals such as unknown sender and not in contacts)
- Level agreement with expected labels: 92.1%

## 7. Size and speed

- Model file: 86 KB, 32768 hashed slots, 1-byte weights
- 0.45 ms per message (Python, dev machine). Phone latency: NOT MEASURED (needs the cheapest team phone)

## 8. REAL test set (real messages, never trained or tuned on)

| Source | Messages | Precision | Recall | F1 | False-alarm rate |
|---|---|---|---|---|---|
| bongoscam | 109 | 100.0% | 100.0% | 100.0% | 0.0% |
| kenya_sms | 117 | 86.4% | 74.5% | 80.0% | 9.1% |
| mendeley_smishing | 1105 | 100.0% | 89.7% | 94.6% | 0.0% |
| team_collected | 796 | 42.1% | 100.0% | 59.3% | 4.3% |
| all | 2127 | 78.2% | 87.5% | 82.6% | 2.0% |

Betting adverts in the test (counted as not-a-scam above): Linda's text model warns on **15 of 45**. In the app, adverts from a registered sender name are not warned on unless they carry a link or another scam sign, so this is the worst case.
