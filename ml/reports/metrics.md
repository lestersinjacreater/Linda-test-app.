# Linda model metrics

Model `2026.10.25-1` · thresholds warn 0.55 / scam 0.8

> **READ THIS FIRST.** Section 8 is the only section measured on **real messages the model never saw** (1683 rows).
> It includes real Kenyan messages (kenya_sms: the Mwatsefu/IwazoLab public set, labels reviewed by Claude and spot-checked by the team; generated-looking messages are kept out of the test).
> Sections 1 to 6 mix synthetic and real messages and show the pipeline works.

## 1. Validation set (wordings the model never saw)

- Precision 98.0% · Recall 92.6% · F1 95.2%
- Confusion matrix: TP 350 · FP 7 · FN 28 · TN 876
- **False-alarm rate on hard negatives:** 1.5% of 263 (target under 2%)
- **False-alarm rate on verified-sender messages (M-Pesa, banks, KPLC, KRA):** 0.0% of 188 (target 0%)

## 2. Held-out campaigns (train without the campaign, then try to catch it)

| Campaign | Detected |
|---|---|
| bongoscam | 43.9% |
| fake_mpesa | 21.9% |
| fuliza_upgrade | 100.0% |
| job_fee | 87.5% |
| kra_refund | 100.0% |
| loan_fee | 74.7% |
| mendeley_smishing | 45.4% |
| other | 21.8% |
| phishing_link | 71.4% |
| pin_request | 100.0% |
| prize | 99.0% |
| reversal | 54.5% |
| sent_by_mistake | 100.0% |
| **average** | **70.8%** |

## 3. Per-category recall (validation)

| Category | Recall |
|---|---|
| bongoscam | 89.8% |
| fake_mpesa | 100.0% |
| fuliza_upgrade | 100.0% |
| job_fee | 100.0% |
| kra_refund | 100.0% |
| loan_fee | 100.0% |
| mendeley_smishing | 83.3% |
| other | 58.8% |
| phishing_link | 91.3% |
| pin_request | 100.0% |
| prize | 100.0% |
| reversal | 100.0% |

## 4. Obfuscation robustness (validation scams)

- Original: 92.6% · Disguised (leetspeak, spacing, emoji): 92.3%

## 5. Fairness sanity check by language (validation)

| Language | Messages | Scam recall | False-alarm rate |
|---|---|---|---|
| en | 881 | 91.4% | 0.7% |
| mixed | 23 | 100.0% | 5.6% |
| sheng | 60 | - | 0.0% |
| sw | 297 | 93.4% | 1.2% |

## 6. Hand-written shared test vectors (never trained on)

- SAFE messages wrongly warned: **0 of 27** 
- Scams caught: 31 of 31; missed: none
- CAUTION cases scoring in the caution band: 0 of 5 (the text model alone cannot judge these: they need the app's context signals such as unknown sender and not in contacts)
- Level agreement with expected labels: 88.9%

## 7. Size and speed

- Model file: 86 KB, 32768 hashed slots, 1-byte weights
- 0.5 ms per message (Python, dev machine). Phone latency: NOT MEASURED (needs the cheapest team phone)

## 8. REAL test set (real messages, never trained or tuned on)

| Source | Messages | Precision | Recall | F1 | False-alarm rate |
|---|---|---|---|---|---|
| bongoscam | 109 | 100.0% | 100.0% | 100.0% | 0.0% |
| kenya_sms | 117 | 73.6% | 76.5% | 75.0% | 21.2% |
| mendeley_smishing | 1105 | 96.9% | 91.2% | 93.9% | 0.2% |
| team_collected | 352 | 35.0% | 100.0% | 51.9% | 3.8% |
| all | 1683 | 81.2% | 87.4% | 84.2% | 1.9% |

Betting adverts in the test (counted as not-a-scam above): Linda's text model warns on **8 of 25**. In the app, adverts from a registered sender name are not warned on unless they carry a link or another scam sign, so this is the worst case.
