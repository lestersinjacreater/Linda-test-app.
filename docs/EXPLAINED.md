# Linda explained in plain English

For teammates: use this to answer a judge's "why did you do it this way?" and "what does this do?".
Updated after every feature. Newest sections go at the bottom.

## What Linda is (30-second version)

Linda is an Android app that reads incoming text messages, decides on the phone itself whether each one is a scam,
and warns the user with a reason in English or Swahili. Nothing is sent to a server: the "brain" runs offline on the phone.

## Phase 0: Foundations

### The Android project (`android/`)
- **Kotlin + Jetpack Compose.** Kotlin is the standard Android language. Compose lets us describe screens as code, which is shorter than the older XML layouts.
- **Package by feature.** Code is grouped by what it does (`features/home`, later `features/detection`, `features/sms`...) rather than by type. Anyone can find "everything about the History screen" in one folder. Features never call each other directly; they share only `core/` and, later, the detection engine.
- **`minSdk 26`.** Linda runs on Android 8.0 and up, which covers cheap phones.
- **Pinned versions.** Every version is fixed (see `LIBRARIES.md`) so a build that works today works tomorrow.

### The database (`core/data/`)
- **Room** is Android's standard wrapper around SQLite, the small database built into every phone.
- One table, `detections`: every message Linda scores, with its sender, level (SAFE/CAUTION/SCAM), score, reasons and time. This feeds the History screen and the "scams caught this month" number.
- `fallbackToDestructiveMigration()`: if we change the table layout, the phone's history is wiped instead of the app crashing. Acceptable for a prototype; it would need real migrations in a shipped product.
- `allowBackup="false"` in the manifest: message data is not copied to Google's cloud backup. Part of our privacy promise.

### The theme (`core/ui/theme/`)
- Dark only, using the colours from the spec: near-black background, cyan accent, hot pink for danger, amber for caution.
- Bigger text than the Android default because many users are older or new to smartphones.

### Navigation (`LindaNavHost.kt`)
- A bottom bar with four screens: Home, Check, History, Settings. Only Home is real so far; the others are placeholders that later features replace.
- Home shows a big "Linda is protecting you" status and the count of scams caught this month, read live from the database.

### Languages
- Every visible string is in `strings.xml` (English) with a Swahili copy in `values-sw/strings.xml`. Nothing is hardcoded.
- For now Linda follows the phone's language setting. A separate in-app language switch comes with the alerts feature (F6).
- **The Swahili text has not been reviewed by a native speaker yet.** Do that before the demo.

### The build pipeline (`.github/workflows/`)
- **`build-apk.yml`:** on every push, GitHub builds the app and runs the unit tests. On `main` (or when triggered by hand) it publishes the APK to a pre-release called `latest-build`, so teammates can download it straight to their phones.
- It also fails the build if the APK exceeds 15 MB.
- **`ml-tests.yml`:** runs the Python tests whenever `ml/` or `shared/` changes.

### The committed debug key (`android/debug.keystore`)
Android only lets you update an installed app if the new version is signed with the *same* key. GitHub builds on a fresh machine each time, so without a fixed key every new build would be rejected with "App not installed". We commit a standard debug key (password `android`, same as every Android developer's default). It is **not** a secret and protects nothing: it is only for sideloaded test builds, never for the Play Store.

### Shared test vectors (`shared/test-vectors.json`)
- 47 example messages, each with the sender and the level Linda *should* give it. Both the Python side and the Kotlin side will be tested against this same file, so they cannot silently disagree.
- Covers every scam type in the spec, in English, Swahili, Sheng and mixed, plus disguised versions (`M-P3SA`, spaced-out letters, emoji).
- **Hard negatives** are real-looking legitimate messages (a real M-Pesa confirmation, a genuine reversal, a bank OTP, a KRA notice, a Safaricom "we never ask for your PIN" warning). They exist to catch false alarms, which the spec calls the enemy.
- All phone numbers are fake placeholders (`07000000xx`); a test enforces that no real-looking number sneaks in.
- `expected_normalised` is empty (`null`) for now. It gets filled in when the text normaliser (F1) is built, because that is when we define exactly how disguises are undone.
- Some legitimate templates (reversal, Fuliza, Safaricom notices, bank sender IDs) are **approximations** of the real wording and are marked in each vector's `note`. Replace them with verified real templates before we quote any accuracy numbers.
- `ml/tests/test_vectors_schema.py` checks the file is well formed and covers everything the spec requires.


## System rebuild, step 2: normaliser, fingerprint and shared test vectors

### The normaliser (`ml/src/features/normalize/normalize.py`)
- **What:** turns "K1m@kosa", "M-P3SA", "t u m a" back into "kimakosa", "mpesa", "tuma" so the model and rules see plain words.
- **Careful part:** `3 0 1 4 5 @ $` are only swapped back inside a word, so money amounts like `Ksh3,140` stay numbers. M-Pesa transaction codes (10 capitals and digits) are left alone for the same reason.
- **Known limit:** a disguised digit at the end of a word is only fixed when it is a single character ("tum4" works, "f33" does not). We chose this over mangling amounts.
- **Why Python and Kotlin must match:** the model was trained on Python's output; if the phone normalised differently, scores would silently drift. The shared test vectors catch that.
- Plain ASCII only, on purpose: Python and Kotlin disagree about lowercasing exotic Unicode.

### The SimHash fingerprint (`ml/src/features/fingerprint/simhash.py`)
- **What:** a 64-bit "similarity fingerprint" of a message. Two messages from the same scam campaign differ by only a few bits, so the radar can say "this new number is sending a known campaign" without ever seeing the text.
- **How:** chop the normalised text into overlapping 3-letter pieces, hash each piece (FNV-1a, a very simple hash), and let every piece vote on each of the 64 bits.
- **Privacy:** a fingerprint cannot be turned back into the message.

### The shared test vectors (`shared/test-vectors.json`)
- 63 messages: real-format M-Pesa/bank/KPLC/KRA messages that must be SAFE, every scam campaign, fake M-Pesa from a personal number, obfuscated, Swahili, Sheng, mixed, and everyday chat.
- All data here is **synthetic**, with placeholder phone numbers (see `DATASETS.md`). Real collected data is still to come.
- Score ranges are provisional until the model exists.

## System rebuild, step 3: the radar (`backend/`)

### What it does
Phones send a tiny report ("this sender looks like a fake M-Pesa, I'm 93% sure") with **no message text**. When enough independent phones agree, the radar marks the sender **confirmed**, tells the network, and answers "is this number dangerous?" lookups. Today: `POST /v1/reports`, `GET /v1/numbers/{msisdn}/risk`, `GET /v1/blocklist`. USSD and payment precheck come later.

### The confirmation rule (`features/confirmation/service.py`)
- One vote per phone per sender in the last 30 minutes. A phone reporting ten times is still one vote.
- A vote is worth **trust x confidence**. Every phone starts at trust 0.3.
- Confirmed when **3 or more phones** voted and the votes add up to **2.4 or more**.
- Trust goes up (+0.1) when a phone's report ends up confirmed and down (-0.05) when the report's window ends without confirmation.
- **Why trust?** Otherwise anyone could run a script that pretends to be 50 phones and get an innocent shop's number blocked.
- **Our addition to the spec's numbers:** all "unproven" phones (trust 0.3 or less) together can add at most 1.2 to the score. Without it, 20 fake new phones would score 20 x 0.3 x 1.0 = 6.0 and confirm an innocent number. With it, they cannot reach 2.4 alone; a confirmation needs established phones. A test (`test_poisoning_...`) proves this, and it fails if the cap is removed.
- Phones that send no integrity token are capped at trust 0.3 (spec). **Consequence:** in the hackathon nobody has a token, so nobody can become trusted, so nothing confirms. See the open question in the step 3 summary.

### Other protections
- Verified senders (`shared/verified_senders.json`: MPESA, KPLC, banks...) are never stored, reported or confirmed.
- Max 30 reports per phone per hour (HTTP 429 after that).
- A burst of 10+ first-time phones on one number within a minute is written to the `security_events` table and logged.
- Reports containing any extra field (such as `text`) are rejected (HTTP 422), so message text can never be stored by accident.
- A new number whose fingerprint is within 6 bits of a confirmed campaign becomes **suspected** immediately, but is never confirmed on the fingerprint alone.
- Telling the network (`POST {TELCO_URL}/network/confirm`) retries 3 times with growing waits; a failure never un-confirms the sender.

### Why it's built this way
Time and the telco call are injected, so the tests need no network and can fast-forward time. The radar's clock decides the window, never the phone's `sent_at`.

## System rebuild, step 4: the model (`ml/`)

### The idea in one paragraph
Linda's text model is a **logistic regression** on **character n-gram TF-IDF**. In plain terms: chop the (normalised) message into overlapping 2 to 5 letter pieces, give each piece a weight (rare pieces count more), and add up the pieces' learned "scaminess" scores into one number between 0 and 1. Chosen because it is tiny (about 360 KB), runs in milliseconds on a cheap phone, needs no ML library in the APK, and we can list the exact pieces that raised a score, which feeds the "every warning explains itself" rule. It copes with Swahili and Sheng and mixed spelling because it looks at letter patterns, not dictionary words.

### Why five extra "context" features (`scoring/metadata.py`)
A real M-Pesa confirmation and a fake one have **identical words**. Only the sender differs. So the model also sees five 0/1 facts: sender is verified, sender is a personal number, there is a link, the text looks like an M-Pesa confirmation, and it looks like one **but the sender is not verified**. The last one is the famous fake-M-Pesa signal. The test `test_same_confirmation_text_flips_with_the_sender` shows one message scoring under 0.05 from `MPESA` and over 0.9 from a personal number.

### Thresholds
Score 0.55 and up is CAUTION (quiet notification), 0.80 and up is SCAM (loud warning). These are the spec defaults; with only synthetic data there is nothing real to tune them on, so do not change them yet.

### The pure-Python scorer (`scoring/scorer.py`)
Uses only the exported JSON and basic arithmetic, no scikit-learn. It is the reference that the Android app and the simulator copy. A test proves it gives the same probability as scikit-learn to nine decimal places, and the shared vectors store its scores so Kotlin must match them too.

### Training and honesty
- **Synthetic data only.** About 110 message templates with random amounts, names and codes. Whole templates are held back for validation so the model is tested on wordings it never saw. Obfuscated copies (leetspeak, spaced letters, emoji) are added to training so disguises do not fool it.
- **No synthetic test set** (rule: test is real data only). The report `ml/reports/metrics.md` opens with a warning that the numbers prove the pipeline, not the product.
- **What the first report already shows:** 0 false alarms on real-format M-Pesa, bank, KPLC and KRA messages; but a weak spot in Swahili job-fee scams and an average of 75% when a whole campaign is hidden from training (fake M-Pesa only 41%, because without those examples the model has no reason to trust the sender signal). Real data is the fix.
- The 4 CAUTION test vectors ("Hi I saw your number online...") score low on purpose: the text alone is innocent; the app's context signals (unknown sender, not in contacts) will push them into CAUTION.

### Running it
`make train` (builds data, trains, evaluates), `make export-model` (writes `shared/models/model-<version>.json`, copies it to Android assets and the simulator, refreshes the expected scores in the test vectors).

## System rebuild, step 5: the mock telco (`simulator/`) and the trust decision

### The trust ceiling (decision of 2026-10-07)
The spec capped phones without a Play Integrity token at trust 0.3, but nobody in the hackathon has a token, so nothing could ever be confirmed. The team chose: **phones without a token can earn trust above 0.3, but only through reports that later get confirmed.** Setting: `RADAR_NO_TOKEN_TRUST_CAP` (default 1.0; set 0.3 to get the old behaviour back, a test covers it).
**The chicken-and-egg problem:** trust is earned by confirmations, so a brand-new radar has no trusted phones and cannot confirm anything. For the demo only, phones whose device id starts with `RADAR_DEMO_TRUSTED_PREFIX` (the simulator's Linda phones, prefix `5111`) start at trust 0.8, standing in for phones with a history. It is empty (off) by default. The poisoning scenario's fake devices use a different prefix, so they are not trusted. **Say this honestly to judges:** in production, trust comes from Play Integrity plus history; the prefix is a demo shortcut.

### What the simulator is
A pretend telco. It makes 300 phones in Kenyan towns (30 with Linda, 90 smartphones without it, 180 basic phones), sends a fake M-Pesa message to 240 of them over 90 simulated seconds, and plays out what would happen:
1. Each Linda phone scores the message with the **real model** (same code as the app, vendored copy). It warns its owner immediately and, if confident, sends the radar a report: sender, type, confidence, fingerprint. **Never the text.**
2. After 3 or more trusted phones agree, the radar confirms the number and calls the telco's `/network/confirm`.
3. The telco warns every recipient who has not been warned (including basic phones, by SMS), and warns on delivery for anything still arriving.
4. Each phone has a "reads the message after N seconds" habit, so we can count **warned before reading**, the headline number.

### Repeatable on purpose
One seed fixes the towns, timings and reading habits, so the demo is identical every run. Each run uses a fresh scammer number and fresh device ids, so the radar's memory of a previous run cannot change the result. `SIM_SPEED=0` runs instantly for tests.

### Scenarios
- **blast**: the main demo.
- **rotating**: the same script from three numbers in turn (shows fingerprint matching helping later numbers).
- **poison**: 20 fake new devices accuse an innocent number; the radar must refuse. Shown on stage as the "we thought about attackers" moment.
- **call**: not built yet (arrives with call screening).

### Tests
Unit tests use a fake radar; `tests/e2e` starts the real radar and simulator as separate processes and checks confirmation, repeatability, poisoning and rotation over HTTP (`make test-e2e`).

### Honest limits
Population behaviour (reading delays, detection latency, how many phones run Linda) is invented. The numbers show how the mechanism works, not what Safaricom's network would measure.

## System rebuild, step 6a: the detector on the phone (`android/.../features/detection/`)

- **Same maths, second language.** `Normalizer`, `SimHash`, `ModelScorer`, `MetadataFeatures` and `Categories` are line-by-line Kotlin versions of the Python in `ml/`. The Kotlin tests read the same `shared/test-vectors.json` and require: identical normalised text, identical fingerprints, and scores within the stored range (plus or minus 0.001) for all 63 messages. If someone changes one side, the other side's tests fail.
- **No ML library on the phone.** `ModelScorer` reads `assets/model.json` and does arithmetic: chop text into 2 to 5 letter pieces, look each up in a table, add up weights, squash to 0..1. About 360 KB, a few milliseconds.
- **`LindaDetector` = the one place that scores.** `ScamDetector.analyse(MessageInput)` returns a `Verdict`: level (SAFE, CAUTION, SCAM), score, reasons in English and Swahili, category, and the SimHash fingerprint for reports. Nothing else in the app may score a message.
- **Context signals** (sender not in contacts + first message: +0.10; sender in contacts: -0.15) nudge a borderline score. They never apply to verified senders, and they cannot turn an innocent message into a SCAM. All numbers sit in `FusionConfig`.
- **Every warning explains itself.** `ReasonCatalogue` holds the plain-language reason per scam type, plus "typical scam wording" built from the n-grams that raised the score most. **The Swahili has not been checked by a native speaker.**
- **Safety tests:** no SAFE vector may ever be warned on, real `MPESA` messages stay SAFE even with unknown-sender context, and the same confirmation text flips from SAFE to SCAM when only the sender changes.

## System rebuild, step 6b/6c: warnings and reporting on the phone

### One path for every message (`MessageProcessor`)
Real SMS, pasted text and (later) demo mode all go through the same function: skip senders the user marked safe; look up whether the sender is in the contacts and whether it is their first message (both used only on the phone); score with `ScamDetector`; if not SAFE, save to history and show the warning. SAFE messages are **never saved**. This is why demo mode behaves exactly like a real SMS.

### The SMS receiver (`SmsReceiver`)
Registered in the manifest so Android wakes it for every incoming SMS, even with the app closed. Long texts arrive in parts; parts from the same sender are joined before scoring. `goAsync()` keeps the receiver alive for the few milliseconds scoring needs. Cheap phones (Tecno, Infinix, Itel, Xiaomi, Samsung) kill background apps, so onboarding shows that brand's battery steps.

### Warnings
Notification title is in the chosen language (Settings), the text is the first reason, and the message itself is never shown on the lock screen. Tapping opens the detail screen: the message, the verdict, every reason, and **Mark as safe**, which adds the sender to an allow-list (a Room table) so Linda stops scoring it. Not built yet: the "I already sent money" recovery button (F10) and voice warnings (F12).

### Reporting (`features/reporting`) and the consent rule
- Off until the user taps **Agree** on the consent card, which lists in English and Swahili exactly what is sent: sender number, scam type, confidence, fingerprint, anonymous device code. Never the message text, contacts or the user's own number. Can be switched off in Settings.
- Only **SCAM** verdicts from a **real phone number** are reported. Verified senders (MPESA, banks...) and pasted or demo text are never reported. Tested in `ReportPayloadTest`.
- Reports go into a database queue first, then `ReportWorker` (WorkManager) sends them when there is network, retrying with growing delays. If the radar is down nothing breaks and nothing is lost. A 4xx answer drops the report so it cannot loop forever.
- The device code is a hash of a random value created at install, never the phone number.
- The server address is empty until set (hidden developer screen, step 6d), so a fresh install sends nothing anywhere.
- `usesCleartextTraffic` is on so the laptop backup radar (plain http) works on demo day. Use https for the cloud radar.

## System rebuild, step 6d: blocklist, caller warnings, developer screen, demo mode

### Blocklist sync (`features/sync`)
Every 15 minutes (Android's minimum) and when the app opens, a background job asks the radar `GET /v1/blocklist?since=<last as_of>` and stores the confirmed scam numbers in a local table. Only the changes travel, so it is tiny. If the radar is unreachable the job retries later and the phone keeps using its last list: **call warnings work offline**. The radar's answer has no "reported by N people" count (the contract does not include it), so the call text says "reported by other Linda users" without a number.

### Caller warnings (`features/calls`)
`LindaCallScreeningService` is asked by Android about every call. It **always lets the call ring** (never blocks or silences): it only shows a heads-up notification. Two reasons to warn, both checked on the phone: the number is on the confirmed blocklist, or it sent this phone a SCAM message in the last 2 hours ("sent you a suspected scam message 8 minutes ago"; the more specific one wins). Android only allows this after the user grants the **call-screening role**; the Settings screen has a button for it (Android 10 or newer).

### Hidden developer screen
Tap the version number in Settings 7 times: radar server address (blank by default, so a fresh install sends nothing anywhere), "Save and sync now", the model version, last sync time, number of scam numbers stored, and **Open demo mode**. Tap 7 times again to hide it.

### Demo mode (`features/demo`)
Seven buttons "receive" a scripted message instantly: fake M-Pesa from a normal number, sent-by-mistake, prize, Fuliza link, PIN request, **a real M-Pesa message (must not warn)** and an ordinary chat message. They use the same `MessageProcessor` as real SMS, so the notification, history entry and counters are real. A last button simulates a call from the fake M-Pesa number, which shows the real call-warning notification. It needs no network. Demo messages are never reported to the radar.

### Known gaps in the Android app
Recovery mode (F10), Family Guardian (F11), voice warnings (F12), "Scan my inbox" (F8) and the "Report" button on the detail screen are not built; they are Phase 2 items in the older spec. Swahili text is unreviewed.
