# CLAUDE.md — Linda: On-Device Scam Shield

> This file is the single source of truth for building Linda.
> Claude Code: read this whole file before writing any code, and re-read the
> relevant section before starting each task.

---

## 1. What Linda is

Linda (Swahili for "protect") is an **Android app that detects mobile-money scams on
the user's phone, in real time, and warns them before they lose money.**

It reads incoming SMS (with permission), scores each message with an on-device
model, and warns the user with a clear reason in English or Swahili. It catches
classic Kenyan scams such as:

- "Nimekutumia pesa kimakosa, tafadhali nirudishie" (sent-by-mistake / reversal scam)
- **Fake M-Pesa confirmations** sent from an ordinary 07XX number instead of the real `MPESA` sender ID
- Fake prizes ("Umeshinda"), fake Fuliza/limit upgrades, fake KRA refunds, fake job "registration fees", PIN requests, phishing links

**Context:** Linda is our entry to the Safaricom interns' *Intelligence Unleashed*
hackathon (Data / AI / ML / Predictive Analytics), **Finance track**. Judges will
install and test the prototype themselves, so **it must actually work on a real,
cheap Android phone.**

### Core principles (never break these)
1. **Works fully offline.** Detection runs on the phone. No server is needed for the core features.
2. **Privacy by design.** SMS content never leaves the device, except when the user explicitly taps "Report", and then only after phone numbers and names are scrubbed.
3. **False alarms are the enemy.** A real M-Pesa, bank, or KRA message flagged as a scam is a critical bug.
4. **Every warning explains itself.** Never show a score without human-readable reasons.
5. **No Safaricom integration.** No Daraja, no M-Pesa APIs, no live credentials of any kind.
6. **Explainable code.** The team must be able to explain every line to judges (hackathon Rule 4). Favour clear code over clever code.

---

## 2. Hackathon rules that affect how we build

| Rule | What it means for the code |
|------|----------------------------|
| All code produced during the official hacking period (5–27 Oct) | Do not copy code from the earlier throwaway spike. Build fresh. |
| Open-source libraries must be declared | Keep `LIBRARIES.md` updated whenever a dependency is added (name, version, licence, why we use it). |
| AI coding tools allowed, but the team must explain every part | Keep `docs/EXPLAINED.md` updated: a plain-English explanation of every module and every non-obvious decision. |
| Safaricom APIs sandbox only, no live credentials | We don't use any Safaricom APIs at all. Never commit secrets. |
| No harmful or discriminatory content | The model must never use sender identity traits (gender, ethnicity, location) as features. |

---

## 3. Environment constraints (important)

- **Nobody on the team uses Android Studio.** Development happens in **GitHub Codespaces** and via **Claude Code**.
- **APKs are built by GitHub Actions** (`.github/workflows/build-apk.yml`) and published to a GitHub pre-release called `latest-build`, so teammates can download the APK straight onto their phones.
- If the Android SDK is available in your environment, run `./gradlew testDebugUnitTest assembleDebug` before committing. If it isn't, run the Kotlin JVM unit tests you can, push, and check the Actions build. **Never leave the `main` branch broken.**
- Use the **Gradle wrapper** (`./gradlew`). If the wrapper jar is missing, generate it in CI with Gradle 8.9 and commit it.
- Do not upgrade pinned versions (section 6) unless a build fails because of them. If you must, explain why in the commit message.

---

## 4. Repository structure

```
linda/
├── CLAUDE.md                  ← this file
├── LIBRARIES.md               ← declared open-source dependencies
├── docs/
│   ├── EXPLAINED.md           ← plain-English explanation of every module
│   ├── ARCHITECTURE.md        ← diagram + data flow (feeds the 2-page technical brief)
│   └── EVALUATION.md          ← model metrics, test results, false-positive report
├── android/                   ← the Android app (Kotlin)
├── ml/                        ← Python: data, training, evaluation, export
├── shared/
│   └── test-vectors.json      ← messages + expected verdicts, used by BOTH Python and Kotlin tests
├── dashboard/                 ← Next.js scam campaign radar (Phase 3)
└── .github/workflows/
    ├── build-apk.yml
    └── ml-tests.yml
```

### Android package layout (package-by-feature)
```
android/app/src/main/java/com/linda/app/
├── LindaApp.kt
├── MainActivity.kt
├── core/
│   ├── data/                 ← Room database, DAOs, entities
│   ├── ui/theme/             ← colours, typography, shared components
│   └── util/                 ← time, phone-number helpers, text normalisation
└── features/
    ├── detection/            ← scoring engine: normaliser, rules, ML model, fusion
    ├── sms/                  ← SMS receiver
    ├── alerts/               ← notifications, TTS voice warnings
    ├── inbox/                ← "Scan my inbox"
    ├── checker/              ← "Is this a scam?" (paste + share-to-Linda)
    ├── history/              ← list of detected messages
    ├── recovery/             ← "I've been scammed, what now?"
    ├── guardian/             ← Family Guardian mode
    ├── calls/                ← warning when a flagged number calls
    ├── report/               ← anonymised reporting (Phase 3)
    └── demo/                 ← demo mode for hack day
```

Each feature folder contains its own screen(s), view model, and logic. Features talk
to each other only through `core/` or through the `detection` engine's public API.

---

## 5. Architecture

```
            Incoming SMS / pasted text / shared text
                            │
                            ▼
                 ┌──────────────────────┐
                 │  Text normaliser     │  undo M-P3SA, K1m@kosa, spacing tricks
                 └──────────┬───────────┘
            ┌───────────────┼────────────────┐
            ▼               ▼                ▼
     ┌────────────┐  ┌────────────┐   ┌──────────────────┐
     │ Rules      │  │ ML model   │   │ Context signals  │
     │ (hard      │  │ (char      │   │ sender type,     │
     │ signals)   │  │ n-gram LR) │   │ in contacts?,    │
     └─────┬──────┘  └─────┬──────┘   │ first time?,     │
           │               │          │ links, timing    │
           │               │          └────────┬─────────┘
           └───────────────┼───────────────────┘
                           ▼
                 ┌──────────────────────┐
                 │  Fusion scorer       │  → Verdict(level, score, reasons)
                 └──────────┬───────────┘
                            ▼
           SAFE (silent) · CAUTION (quiet banner) · SCAM (loud warning)
                            │
         ┌──────────────────┼────────────────────┐
         ▼                  ▼                    ▼
   Notification +     History (Room DB)    Guardian alert,
   voice warning                           call warning
```

### The detection engine's public API
```kotlin
interface ScamDetector {
    fun analyse(input: MessageInput): Verdict
}

data class MessageInput(
    val body: String,
    val sender: String?,          // null when pasted/shared
    val receivedAt: Long,
    val senderInContacts: Boolean?,
    val firstMessageFromSender: Boolean?,
)

enum class RiskLevel { SAFE, CAUTION, SCAM }

data class Verdict(
    val level: RiskLevel,
    val score: Float,             // 0.0–1.0
    val reasons: List<Reason>,    // each has an English + Swahili string
    val modelVersion: String,
)
```
Everything else in the app (receiver, checker, inbox scan, demo mode) calls
`ScamDetector.analyse()`. Nothing else is allowed to score messages.

---

## 6. Tech stack (pinned)

**Android**
- Kotlin 2.0.21, Android Gradle Plugin 8.7.3, Gradle 8.9, JDK 17
- `compileSdk 35`, `targetSdk 35`, **`minSdk 26`** (must run on cheap phones)
- Jetpack Compose (Compose BOM 2024.12.01) with Material 3, Kotlin Compose compiler plugin 2.0.21
- Room 2.6.1 with KSP (2.0.21-1.0.28)
- Android built-in `TextToSpeech` for voice warnings
- **No** Firebase, analytics, ads, or tracking SDKs in the Android app
- Keep the APK under **15 MB**

**ML (Python 3.11)**
- pandas, scikit-learn, numpy for the baseline
- Stretch: Hugging Face `transformers` + a small multilingual model, exported to TensorFlow Lite
- pytest for tests

**Dashboard (Phase 3 only)**
- Next.js (App Router) + TypeScript + Tailwind, `src/features/...` folder structure
- Supabase free tier for anonymised reports
- **Every dashboard screen must render with fallback hardcoded sample data** if Supabase is unreachable, so the demo can never show an empty screen

---

## 7. Features and acceptance criteria

Priorities: **P0 = must work on demo day. P1 = what wins. P2 = only if P0 and P1 are solid.**

### Phase 0 — Foundations (P0)
- Project skeleton, Gradle setup, CI builds a debug APK and publishes it to the `latest-build` release.
- Dark theme, Room database, navigation between screens.
- `shared/test-vectors.json` created with at least 40 cases (see section 9).
- **Done when:** a teammate downloads the APK from Releases, installs it, and the app opens.

### Phase 1 — Core detection (P0)

**F1. Text normaliser** (`features/detection`)
- Lowercase, undo leetspeak (3→e, 0→o, 1→i, 4→a, 5→s, @→a, $→s) **only when the character touches a letter**, so amounts like "2,500" survive.
- Collapse punctuation and spacing; map "m pesa", "m.pesa", "m-p3sa" → "mpesa".
- **Must produce identical output to the Python normaliser** in `ml/` (verified by the shared test vectors).

**F2. Rules layer** — hard, explainable signals:
- **Fake M-Pesa check:** message looks like an M-Pesa confirmation (a 10-character alphanumeric transaction code with at least one letter and one digit, "Confirmed", and a "Ksh" amount) **but the sender is not a trusted sender ID** → strong scam signal. Reason: "This looks like an M-Pesa message but did not come from M-Pesa. You have not received any money."
- PIN request → strong signal.
- Phishing link check: links present, lookalike domains (e.g. contains "safaricom" or "mpesa" but isn't an official domain), risky endings (.xyz, .top, .click), URL shorteners.
- Trusted sender list (`MPESA`, `Safaricom`, known bank shortcodes) reduces risk but **never** fully overrides a strong content signal.

**F3. ML model** — character n-gram TF-IDF + logistic regression, trained in `ml/`, exported as `android/app/src/main/assets/model/linda_model.json` (vocabulary, IDF weights, coefficients, intercept, version). Kotlin loads it and computes the probability. The **top contributing n-grams** become extra explanation material.

**F4. Fusion scorer**
- Combine rule signals, ML probability, and context signals (unknown sender, not in contacts, first message from this number, personal 07XX/01XX number) into a final score.
- Thresholds live in one config object, tuned from `docs/EVALUATION.md`, not scattered in code.
- Three levels: `SAFE` (no alert), `CAUTION` (quiet notification), `SCAM` (high-priority notification).

**F5. SMS receiver** (`features/sms`)
- Manifest-registered receiver for `SMS_RECEIVED`; stitch multi-part messages per sender.
- Runs `ScamDetector.analyse()`, saves the result to history, triggers alerts.
- Must work with the app closed and screen off. The first-run onboarding must guide users to disable battery optimisation, **with brand-specific instructions for Tecno, Infinix, Itel, Samsung, and Xiaomi.**

**F6. Warnings** (`features/alerts`)
- Notification title and body in the user's chosen language (English or Swahili).
- Tapping opens a detail screen: the message, the verdict, every reason, and actions: "Report", "Mark as safe", "I already sent money →" (opens Recovery).
- "Mark as safe" adds the sender to a user allow-list.

**F7. "Is this a scam?" checker** (`features/checker`)
- Paste box, plus **Android share-sheet target** so users can share text from WhatsApp or any app into Linda. Needs no SMS permission.

**F8. Scan my inbox** (`features/inbox`)
- Requests `READ_SMS`, scores the last 90 days of messages in the background with a progress bar, then shows "Linda found N suspicious messages" with a grouped list.
- Must finish scanning 2,000 messages in under 20 seconds on a low-end phone.

**F9. History** (`features/history`) — searchable list of flagged messages, filter by level.

**Phase 1 done when:** every sample in the demo script (section 11) behaves correctly on a real phone, and a real M-Pesa message produces no alert.

### Phase 2 — What wins (P1)

**F10. Recovery mode** (`features/recovery`)
- Step-by-step guide for someone who already sent money: act fast, request a reversal through Safaricom's official process, report the number to 333, contact their bank if relevant, report to police for large amounts.
- **Pre-filled report text** they can copy.
- Keep all official numbers and steps in one config file with a `// VERIFY BEFORE DEMO` comment. The team must confirm them against official Safaricom sources.

**F11. Family Guardian mode** (`features/guardian`)
- The protected person (e.g. a parent) adds a guardian's phone number.
- When a `SCAM`-level message arrives, Linda **sends an SMS alert to the guardian**: "Linda alert: Mum received a suspected 'sent by mistake' scam at 4:12 PM. Consider calling her." (Needs `SEND_SMS`. Fine for sideloaded builds. This avoids needing any backend.)
- Rate-limit to at most one guardian alert per sender per 6 hours.
- The protected person must explicitly opt in, and can see and turn off guardian alerts at any time.

**F12. Voice warnings** (`features/alerts`) — read `SCAM` warnings aloud with `TextToSpeech`, in Swahili when available, otherwise English. Toggle in settings, on by default for Guardian-protected users.

**F13. Suspicious caller warning** (`features/calls`)
- If a number that sent a `SCAM` message in the last 2 hours calls, show a high-priority heads-up warning: "This caller sent you a suspected scam message 8 minutes ago."
- Implement with a heads-up notification first. Only attempt a screen overlay if time allows.

**F14. Demo mode** (`features/demo`) — **critical for hack day**
- Hidden toggle (tap the logo 7 times) that lets a presenter "receive" any sample scam message instantly, going through the exact same pipeline as a real SMS.
- A backup in case the venue has no network signal.

### Phase 3 — Campaign radar (P2)

**F15. Anonymised reporting** (`features/report`)
- "Report" sends **only** the scrubbed message text, the verdict, the model version, and a timestamp rounded to the hour, to Supabase.
- Scrub phone numbers, M-Pesa codes, names following "from"/"kutoka", and amounts before sending. Show the user the exact text being sent and ask for confirmation.

**F16. Campaign clustering** (`ml/radar/`) — embed reported messages, cluster them into scam campaigns, compute week-over-week growth per campaign.

**F17. Radar dashboard** (`dashboard/`) — dark, vivid Next.js dashboard: campaign clusters, growth trends, example messages. **Falls back to hardcoded sample data** when the database is unreachable.

---

## 8. ML pipeline (`ml/`)

```
ml/
├── data/
│   ├── raw/                 ← gitignored; never commit raw personal data
│   ├── processed/           ← cleaned, scrubbed, labelled
│   └── README.md            ← data sources, collection method, consent, schema
├── linda_ml/
│   ├── normalise.py         ← MUST match Kotlin normaliser exactly
│   ├── augment.py           ← obfuscation augmentation (leetspeak, spacing, emoji noise)
│   ├── features.py
│   ├── train_baseline.py
│   ├── evaluate.py
│   └── export_model.py      ← writes linda_model.json for Android
├── notebooks/               ← exploration only; real logic lives in linda_ml/
└── tests/
```

### Data rules
- Dataset CSV columns: `id, text, label (scam|legit), campaign, source, is_hard_negative, language (en|sw|sheng|mixed)`.
- **Scam messages:** real ones collected from team members and families. Scrub all phone numbers, names, and M-Pesa codes before they enter `processed/`.
- **Legit messages:** built from public formats (M-Pesa confirmation templates, bank alerts, delivery notices, KRA notices) and ordinary chat-style messages. **Never** collect people's private SMS.
- **Hard negatives** are mandatory: real-looking M-Pesa reversals, Fuliza notices, bank OTPs, KRA reminders, delivery updates.
- Any synthetic or LLM-generated examples are marked in the `source` column and declared in `ml/data/README.md`.

### Evaluation (goes into `docs/EVALUATION.md`)
1. **Held-out campaign split** (the headline result): for each campaign, train without it and test whether Linda still catches it. Report the average detection rate on unseen campaigns.
2. Standard metrics on a stratified test set: precision, recall, F1, confusion matrix.
3. **False-positive rate on hard negatives specifically.** Target: 0% on real M-Pesa/bank templates, under 2% on all hard negatives.
4. Obfuscation robustness: detection rate on augmented (disguised) scam messages vs originals.
5. Latency and model size, measured on the cheapest team phone.
6. Fairness sanity check: error rates for English vs Swahili vs Sheng messages should be comparable.

### Export contract (`linda_model.json`)
```json
{
  "version": "baseline-2026-10-12",
  "ngram_range": [2, 5],
  "analyzer": "char_wb",
  "vocabulary": { "kimak": 0, "...": 1 },
  "idf": [1.23, 4.56],
  "coef": [0.87, -0.12],
  "intercept": -1.9,
  "sublinear_tf": true,
  "norm": "l2"
}
```
The Kotlin implementation must reproduce scikit-learn's probabilities to within ±0.001 on every shared test vector. Add a unit test for this.

---

## 9. Testing

- **`shared/test-vectors.json`**: each entry has `text`, `sender`, `expected_level`, `expected_normalised`, and `must_include_reason` (optional). Both `ml/tests` (pytest) and `android` JVM unit tests run against the same file. If Python and Kotlin disagree, that's a bug.
- Minimum coverage in the vectors: every scam campaign type, fake M-Pesa from a 07XX number, a real M-Pesa message from `MPESA`, a real reversal message, bank OTPs, KRA notices, obfuscated scams, Swahili, Sheng, and mixed-language messages, and ordinary chat.
- Unit tests for normaliser, rules, fusion thresholds, scrubber (Phase 3), and the guardian rate limiter.
- `ml-tests.yml` runs pytest on every push touching `ml/` or `shared/`.
- **Manual device checklist** in `docs/EVALUATION.md`: background detection with app closed and screen off for 10+ minutes, on at least one Tecno/Infinix/Itel phone and one Samsung.

---

## 10. UI and design direction

- **Dark, vivid, confident.** Near-black background (`#0B0F1A`), card surfaces (`#151B2E`), electric cyan accent (`#00E5FF`), hot pink for danger (`#FF2E88`), amber for caution (`#FFC940`).
- Home screen shows a large **protection status** ("Linda is protecting you" + count of scams caught this month) rather than a boring list.
- The scam warning screen should feel urgent but calm: big verdict, plain reasons, one clear next action.
- **Every user-facing string lives in `strings.xml` with a Swahili translation in `values-sw/strings.xml`.** No hardcoded UI text.
- Large tap targets and readable text sizes: many users will be older or new to smartphones.
- Must look good on a 5.5-inch, 720p screen.

---

## 11. Demo script (the app must support this exactly)

1. A teammate sends a **fake M-Pesa confirmation** from their normal number → 🚨 notification: "This did not come from M-Pesa. You have not received any money." Voice warning plays.
2. The guardian's phone receives the **Family Guardian SMS alert**.
3. The same teammate **calls** → suspicious caller warning appears.
4. **Scan my inbox** on a teammate's real phone → "Linda found N suspicious messages."
5. Open **Recovery mode** for a user who already sent money.
6. (Phase 3) Open the **campaign radar** dashboard.
7. Backup: **Demo mode** replays all of the above without network.

Sample messages for testing:
- `Nimekutumia 2,500 kimakosa, tafadhali nirudishie haraka`
- `QK7RT2XY9P Confirmed. You have received Ksh2,500.00 from JOHN KAMAU on 12/10/26 at 4:12 PM. New M-PESA balance is Ksh3,140.00.` (sent from a 07XX number)
- `Hongera! Umeshinda Ksh 50,000. Tuma registration fee ya Ksh 500 upate zawadi yako.`
- `Dear customer, your Fuliza limit has been increased. Click http://safaricom-bonus.xyz to activate`
- `Hey, are we still meeting at 6?` (must be SAFE)

---

## 12. How Claude Code should work in this repo

1. **Work one phase at a time, one feature at a time.** Don't start Phase 2 until Phase 1's "done when" criteria pass.
2. **Before coding a feature**, briefly state your plan: files you'll create or change, and how you'll test it.
3. **Small, focused commits** with clear messages (`feat(detection): add fake M-Pesa rule`).
4. **After each feature**, update `docs/EXPLAINED.md` with a plain-English explanation a teammate could use to answer a judge's question, and update `LIBRARIES.md` if you added a dependency.
5. **Don't add dependencies casually.** Prefer the Android SDK and Kotlin standard library. Justify every new library.
6. **Never** commit secrets, real phone numbers, real personal messages, or raw data.
7. **Never** weaken privacy rules (section 1) to make a feature easier.
8. If something in this spec is ambiguous or seems wrong, **ask instead of guessing.**
9. If a build fails in CI, fix it before doing anything else.

---

## 13. Out of scope

- Play Store publishing (SMS permissions require special approval; we sideload the APK)
- iOS
- Any Safaricom, M-Pesa, or Daraja integration
- Blocking or deleting messages (Linda warns; the user decides)
- Mule-account/transaction-graph detection (mention in the pitch as future work with Safaricom data)
- Reading WhatsApp notifications (future work; the share-to-Linda checker covers it for now)
