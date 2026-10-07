# Evaluation

Every number comes from a real run, never an estimate. **There is no real test data yet**, so the model numbers are from synthetic and hand-written messages. They show the pipeline works; they are not evidence about real scams. The full, regenerated report is `ml/reports/metrics.md` (`make train`).

## Targets (from the spec)

| Metric | Target |
|---|---|
| False positives on real M-Pesa / bank templates | 0% |
| False positives on all hard negatives | under 2% |
| Kotlin vs scikit-learn probability difference | within ±0.001 on every shared test vector |
| Inbox scan | 2,000 messages in under 20 s on a low-end phone |
| APK size | under 15 MB (checked automatically in CI) |

## Results (synthetic data only, model 2026.10.07-1, 2026-10-07)

| Test | Result | Notes |
|---|---|---|
| Held-out campaign split (avg detection on unseen campaigns) | 75.0% | synthetic; fake M-Pesa only 41% when held out |
| Precision / recall / F1 (validation, unseen wordings) | 100% / 91% / 95.3% | synthetic validation, NOT a real test set |
| False alarms, real M-Pesa/bank/KPLC/KRA templates | 0 of 120 (validation), 0 of 27 safe test vectors | target 0% |
| False alarms, all hard negatives | 0 of 180 (validation) | target under 2% |
| Obfuscation robustness | 91.0% original, 88.5% disguised | synthetic |
| Kotlin vs Python parity | identical normalised text and fingerprint, score within 0.001 on all 63 vectors | checked by `ParityTest` in CI |
| Model size, Python speed | 360 KB, 0.26 ms per message (dev machine) | |
| Latency on the cheapest team phone | NOT MEASURED | needs a real device |
| Fairness (English vs Swahili vs Sheng) | en 100%, sw 82% scam recall; 0% false alarms in all | Swahili job-fee wording is a known miss |
| Simulated blast (mock telco) | confirmed about 29 s in; 99% warned before reading | invented population habits |
| End-to-end (real radar + simulator, over HTTP) | confirmation, repeatability, poisoning blocked, rotating numbers | `make test-e2e` |

## Fusion thresholds

Spec defaults (warn 0.55, scam 0.80) in the model file. Not tuned: there is no real data to tune on. Context nudges (unknown first sender +0.10, known contact -0.15) are in `LindaDetector.FusionConfig`.

## Manual device checklist

Run on at least one Tecno/Infinix/Itel phone and one Samsung.

- [ ] APK from the `latest-build` release installs and the app opens (Phase 0 done-when)
- [ ] Background detection works with the app closed and the screen off for 10+ minutes
- [ ] A real M-Pesa message produces no alert
- [ ] Every sample in the demo script (CLAUDE.md section 11) behaves correctly
- [ ] Looks good on a 5.5-inch, 720p screen
- [ ] Battery-optimisation instructions are correct for Tecno, Infinix, Itel, Samsung, Xiaomi
- [ ] Swahili text reviewed by a native speaker
- [ ] **Recovery mode numbers and steps verified on Safaricom's OFFICIAL pages / the M-PESA app** (456 reversal and its 24-hour window, 333 fraud report, 100 / 200 customer care, DCI hotline 0800 722 203, 999 / 112). Today they are only corroborated by news reports because Safaricom's site could not be opened from the build environment. Table of what was checked: `RecoveryConfig.kt`. Then delete the `// VERIFY BEFORE DEMO` comments you have confirmed.
- [ ] Recovery mode: from a warning, "I already sent money" opens it with the scammer's number and date filled in; from Home it opens empty; "Just now" shows the reversal step and "More than 24 hours ago" does not; the call and message buttons open the phone's own dialer and messages app and never send anything themselves; the copied report text pastes correctly
- [ ] Recovery mode works with airplane mode on (no network needed)
- [ ] Swahili recovery text reviewed by a native speaker (it tells people where to send money-related reports, so wrong wording matters)
- [ ] Consent screen: reports stay OFF until Agree is tapped; Settings switch turns them off again
- [ ] With the radar address set in the developer screen (tap version 7 times): a demo fake M-Pesa message does NOT create a report (demo is never reported), a real one from a normal number does
- [ ] Airplane mode: a scam SMS still warns, the report waits in the queue ("Reports waiting") and sends after reconnecting
- [ ] Call warning: grant the call-screening role in Settings, then call from a number that just sent a scam SMS (or one on the blocklist); the call still rings, with a warning on top
- [ ] Demo mode: all seven buttons behave as labelled, and the real M-Pesa and chat samples do NOT warn
- [ ] Family Guardian: off by default; switching it on asks for the SMS permission; a test alert reaches a second phone; a demo-mode fake M-Pesa message sends the guardian ONE alert and sending it again within 6 hours does not send another (and the log shows it); a real M-Pesa message and a pasted message send nothing; switching it off stops alerts; the alert fits in one SMS
- [ ] Voice warnings: Settings > Test the voice speaks; a demo-mode fake M-Pesa message is read aloud when voice is on and the phone is not on silent; it is NOT read on silent or vibrate, during a call, or for a pasted message; sending two scams within 30 s speaks only once; switching on Family Guardian turns voice on by default until the person changes it; on a phone with no Kiswahili voice, Settings says so and English is spoken; with the screen off and the app closed the voice still plays fully
- [ ] **Scan my inbox speed on the cheapest team phone** (spec: 2,000 messages in under 20 s): note the "Checked N messages in X seconds" line and scale to 2,000. Dev machine: about 0.5 s, so the phone result is the unknown. Record it in the results table above.
- [ ] Scan my inbox: the permission is asked only when Scan is tapped; the progress bar moves; Stop keeps results; a real M-Pesa message in the inbox is NOT flagged; scanning twice does not duplicate History; Home's monthly counter does not jump after a scan; no notification, voice, guardian SMS or report is produced by a scan
- [ ] Sender IDs COOPBANK and KRA confirmed as the real IDs those organisations use
