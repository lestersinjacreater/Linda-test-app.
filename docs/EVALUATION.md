# Evaluation

Nothing has been measured yet: there is no model. Every number below is filled in from real runs, never estimated.

## Targets (from the spec)

| Metric | Target |
|---|---|
| False positives on real M-Pesa / bank templates | 0% |
| False positives on all hard negatives | under 2% |
| Kotlin vs scikit-learn probability difference | within ±0.001 on every shared test vector |
| Inbox scan | 2,000 messages in under 20 s on a low-end phone |
| APK size | under 15 MB (checked automatically in CI) |

## Results

| Test | Result | Date | Model version |
|---|---|---|---|
| Held-out campaign split (avg detection on unseen campaigns) | not run | | |
| Precision / recall / F1 (stratified test set) | not run | | |
| False-positive rate, real M-Pesa/bank templates | not run | | |
| False-positive rate, all hard negatives | not run | | |
| Obfuscation robustness (disguised vs original) | not run | | |
| Latency and model size on cheapest team phone | not run | | |
| Fairness check: English vs Swahili vs Sheng error rates | not run | | |

## Fusion thresholds

Not tuned yet. They will be set from the results above and kept in one config object.

## Manual device checklist

Run on at least one Tecno/Infinix/Itel phone and one Samsung.

- [ ] APK from the `latest-build` release installs and the app opens (Phase 0 done-when)
- [ ] Background detection works with the app closed and the screen off for 10+ minutes
- [ ] A real M-Pesa message produces no alert
- [ ] Every sample in the demo script (CLAUDE.md section 11) behaves correctly
- [ ] Looks good on a 5.5-inch, 720p screen
- [ ] Battery-optimisation instructions are correct for Tecno, Infinix, Itel, Samsung, Xiaomi
- [ ] Swahili text reviewed by a native speaker
- [ ] Recovery-mode phone numbers and steps verified against official Safaricom sources
