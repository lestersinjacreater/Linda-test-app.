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

## System rebuild, step 7: the dashboard (`dashboard/`)

### What the audience sees
One screen, built to be read from the back of a room. Left: a map of Kenya with a dot for each of 300 simulated phones. Grey = quiet, amber = just received the scam, red = a Linda phone caught it on the device, green = warned. Cyan-ringed circles are Linda phones, squares are basic phones, plain circles are smartphones without Linda. Right: a clock ("time since the scam started"), four big numbers (warned before reading, basic phones warned first, time for the first phone to catch it, time for the radar to confirm), the scam numbers the radar knows about, the result of the attack test, and a plain-English feed.

### How it stays correct: one pure function
Every number and colour is rebuilt from the stream of events by a single function (`lib/state.ts`, `reduce`). A phone's colour only moves forward (grey, amber, red, green), so a late event cannot un-warn someone. Live mode and replay mode use the same function, so they cannot disagree. 13 tests cover it, including: replaying the recorded run gives exactly the headline numbers the simulator reported.

### The 5-second safety net
The dashboard keeps a WebSocket open to the simulator and reconnects automatically. If it has been down for 5 seconds, it replays `lib/fallback/demo-events.json`, a **real run recorded from the real radar and simulator** (`make record-fallback`), and shows an amber "REPLAY" badge so the team knows. When the connection returns it switches back to live. We tested it by killing the simulator while the page was open: "CONNECTING..." after 2 s, "REPLAY" after 7 s. Controls are disabled in replay because there is nothing to command.

### Where the numbers come from
Live, the four big numbers come from the simulator's `/metrics` (the single source of the headline numbers). In replay they are computed from the recorded events.

### Presenter controls
R reset, B blast, O rotating numbers, P attack test. Each scenario starts with a clean map. The attack test shows "blocked. The innocent number was NOT confirmed." (or a red warning if it ever were).

### Safety and limits
- The dashboard only receives simulator events. They contain no message text, and a test checks the recorded file for `text`, `body` and `message` fields.
- No map library: the Kenya outline is a small hand-drawn shape, so there is no map server to fail on stage. It is simplified, not survey-accurate.
- The radar logs a "possible poisoning" warning during the genuine demo blast too, because its phones are all first-time reporters. It only logs and never blocks; in real use phones would have a history.
- `NEXT_PUBLIC_TELCO_API` / `NEXT_PUBLIC_TELCO_WS` are fixed at build time, and the BROWSER connects to them, so on a cloud server set them to the server's public address.

## Production setup on one server (`docker-compose.prod.yml`, `deploy/Caddyfile`, `docs/DEPLOY.md`)

### The idea
One small VPS runs five containers: Postgres, the radar, the mock telco, the dashboard, and **Caddy**. Only Caddy has public ports (80 and 443). It gets a free HTTPS certificate by itself and forwards only an allow-list of paths: `/` to the dashboard, `/radar/*` to the radar (what phones use), and the simulator's read-only feeds. The simulator's internal calls (`/network/confirm`, `/deliveries`, `/history`) cannot be reached from outside, and starting or resetting a demo needs the presenter's password. So nobody on the internet can run a scam blast on your stage or fake a radar confirmation by calling the telco directly.

### How the dashboard finds the server
The browser connects to `https://DOMAIN/telco` and the live feed to `wss://DOMAIN/telco/events` (worked out from the same setting, `PUBLIC_URL`). That address is baked in when the dashboard image is built, so changing it means rebuilding (`make prod-up` does).

### A bug that only the real database found
Until now the radar was tested on SQLite. Running it on real Postgres, every report was rejected: the code inserted the report before the sender row it points to. SQLite does not enforce "foreign keys" unless asked, so every test passed; Postgres always enforces them. The fix is one `flush()` that writes the device and sender first, and the test database now switches foreign-key checking on, so this class of mistake fails in the tests from now on. Lesson for the judges' Q&A: we tested on the production database engine, and it found a real defect.

### Checking a deployment
`make prod-check` (`scripts/smoke_prod.py`) looks at the server from outside: pages load, private paths are closed, the password is enforced, then it plays one blast and confirms the radar and blocklist agree. We ran it here against real Postgres behind real Caddy: all 14 checks passed with the same headline numbers as the simulator alone (99.2% warned before reading, confirmed at 28.5 s).

## Recovery mode (`features/recovery`)

### What it is
The screen for someone who has **already sent money** to a scammer ("I've been scammed, what now?"). It opens from the warning screen ("I already sent money →", with the scammer's number and date already filled in) and from a card on the Home screen. It needs no network and no server, and it sends nothing anywhere.

### What it does
1. Asks a few optional questions: when the money was sent (just now, in the last 24 hours, longer ago), how much, the number it went to, the M-PESA transaction code, whether it was paid from a bank or card.
2. Shows a checklist, in order, that changes with the answers (`RecoveryPlan`, a pure function with unit tests): stop and send no more; keep the evidence; **ask for a reversal by forwarding your payment confirmation to the reversal short code, only while the 24-hour window is open**; call customer care; forward the scam message to the fraud short code; call your bank (only if you paid from a bank); report to the police for large amounts (optional for small ones); and a warning that scammers come back pretending to help recover money for a fee.
3. Buttons next to steps **open the phone's dialer or messages app** with the number filled in. Linda never calls or sends for the person: they press the button themselves.
4. Writes a ready-made report in English and in Kiswahili (`ReportText`) with the scammer's number, amount, transaction code, date and what the scam looked like, with a Copy button, to paste into an SMS, a bank form or a police statement. The scam message itself is included only if the person switches that on. If a fact is missing it says "(not provided)" rather than leaving a blank or inventing one.

### Where the official numbers live, and how sure we are
Every number, the 24-hour window and the "large amount" threshold are in **one file**, `RecoveryConfig.kt`, each marked `// VERIFY BEFORE DEMO`, and the file lists how each was checked. **Be straight with judges and teammates about this:** Safaricom's own website could not be opened from our build environment, so none of the numbers is confirmed on an official Safaricom page yet. 456, 333, 100 and 200 agree across several Kenyan news and explainer sources; the DCI hotline agrees across news reports quoting the DCI; 999 / 112 are the well-known emergency numbers but our search did not confirm them; the Ksh 10,000 "large amount" line is our own decision. A teammate must check them on Safaricom's pages or in the M-PESA app and then remove the markers.

### Honest limits
- Linda cannot reverse money. The screen says so. Whether money comes back depends on Safaricom, the bank, and whether the scammer already withdrew it.
- The reversal request in the guide is Safaricom's published self-service route; in a fraud case Safaricom may handle it differently, which is why customer care and the fraud report are also steps.
- The Swahili text is unreviewed.

## Family Guardian (`features/guardian`)

### What it is
A parent (or anyone) names a **guardian**, such as a son or daughter. When the parent's phone receives a message Linda is sure is a scam, Linda sends the guardian **one short SMS**: "Linda alert: Mum received a suspected "sent by mistake" scam at 4:12 PM. Consider calling them." The guardian can phone and talk the parent out of sending money. It needs no server: the SMS goes straight from phone to phone.

### The rules that protect the person it protects
- **Opt-in, off by default.** Nothing is sent until the protected person fills in the guardian's number and their own name, and turns the switch on. The SMS permission is asked for only at that moment.
- **They see everything.** The screen shows the exact wording that will be sent and a log of every alert sent. They can send a test alert, and switch it off at any time.
- **Only what the alert needs.** The SMS has the person's name, the kind of scam and the time. It never contains the scam message, the scammer's number, contacts or balance. A unit test checks this.
- **No spam.** At most **one alert per scammer number every 6 hours** (`GuardianRules.RATE_LIMIT_MS`). The limit is kept in the database, so restarting the phone does not reset it. Failed sends and tests do not use it up.
- **Only real signals.** Only SCAM-level verdicts, never CAUTION; never for pasted text (the person checked that themselves) and never for verified senders.
- **It fits in one SMS.** Names are shortened to 20 characters, and a test checks every alert in English and Kiswahili stays under 160 characters.
- The English wording avoids he/she/her/him ("Consider calling them"). Kiswahili has no gendered pronoun.

### How it connects
`MessageProcessor` calls `GuardianService.maybeAlert` after saving a SCAM, for real SMS and for demo mode (so demo step 2, "the guardian's phone receives the alert", works with no network of ours; it uses the phone's normal SMS). `GuardianPolicy.decide` returns Send or the exact reason for skipping, and the unit tests cover every reason.

### Honest limits
- "Sent" means the alert was **handed to the phone's SMS system**. The carrier can still fail to deliver it, and Android does not tell us. That is why there is a test button.
- It uses the person's own SMS balance and the SEND_SMS permission, which is fine for a sideloaded APK but is exactly the kind of permission the Play Store restricts (we do not publish there).
- The alert tells the guardian a scam arrived, which is a deliberate disclosure the person agreed to. Explain this plainly if a judge asks about privacy.
- Voice warnings (F12) "on by default for guardian-protected users" are not built yet.

## Voice warnings (`features/alerts/VoiceWarnings.kt`, rules in `VoiceLogic.kt`)

### What it does
When Linda is sure a message is a scam, it also **says so out loud**, using the phone's built-in text-to-speech (no extra library): "Linda warning. This message is probably a scam. [the first reason]. Do not send money and do not share your PIN." It is for people who may not read a notification fast, such as older people or people with low vision. The text notification always appears too.

### The rules (all unit tested in `VoicePolicy`)
- Only **SCAM** verdicts, only for real or demo texts (not pasted text: the person is already looking at the answer).
- **Quiet if the phone is on silent or vibrate** (the person asked for quiet, e.g. at night) and **never during or while ringing for a call**.
- **At most once every 30 seconds**, so a burst of scam texts does not become a wall of talking.
- **On by default only for people with Family Guardian switched on**; once the person flips the switch themselves, their choice always wins.
- The script contains no digits, no message content and no phone numbers.

### Language
If the person's language is Kiswahili and the phone has a Kiswahili voice, Linda speaks Kiswahili; otherwise English. Many cheap phones have **no Kiswahili voice installed**, so Settings checks and says so, and has an "Install voices" button and a "Test the voice" button. Note: the spec said "Swahili when available, otherwise English"; we follow the person's chosen language first, so someone who picked English is never spoken to in Kiswahili.

### Keeping it alive
The SMS receiver is only allowed a few seconds, so it waits for the voice (at most 7 seconds) before finishing, so the phone does not stop Linda mid-sentence. On very aggressive battery savers the voice can still be cut off; the notification is always shown first, which is why the voice is an extra and not the only warning.

### Honest limits
Not tested on a real phone yet (no Android device or emulator here). Voice quality depends entirely on the voices installed on the phone.

## Scan my inbox (`features/inbox`)

### What it does
Reads the last **90 days** of text messages **on the phone**, checks each one with the same detector as a live SMS, and shows "Linda found N suspicious messages" grouped by sender, worst first (any SCAM before caution-only, then most messages, then newest). Tapping a sender opens the newest message with the reasons. A progress bar shows "Checked 1,240 of 2,000", with a Stop button. It is the demo's step 4: run it on a teammate's real phone.

### Decisions worth explaining
- **Permission only when needed.** `READ_SMS` is asked for only when the person taps Scan, with a plain explanation first. Nothing is read otherwise.
- **Nothing leaves the phone, and no alarms for old mail.** A 90-day backlog must not trigger 200 notifications, voice warnings, guardian SMS or radar reports. The scan makes none of them. Flagged messages are only saved in History on the phone.
- **No double counting.** Scanning twice does not add duplicates (a saved message is found again by its sender and time). Inbox finds are marked `source = inbox` and are **not** counted in Home's "scams caught this month", which means scams Linda stopped live.
- **One contact lookup per sender, not per message.** Asking Android "is this number in my contacts?" is the slow part on a cheap phone, so the answer is remembered per sender.
- **The "first message from this sender" signal is not used.** A 90-day window cannot tell what was really the first message, so Linda does not guess.
- **Senders the person marked safe are skipped**, and verified senders (M-Pesa, banks, KPLC, KRA) are never flagged.
- **Cancel keeps what was found.** Stopping early still shows and saves the results so far and says "Stopped early".

### Speed (the spec asks for 2,000 messages in under 20 seconds on a low-end phone)
- Measured on a development machine, **2,000 messages are scored in about 0.5 seconds** (a test set that is half scams, which is far more scams than a real inbox). A cheap phone is many times slower, so **this is NOT yet proof of the 20-second target.** The result screen shows "Checked N messages in X seconds", so the first teammate to run it on a Tecno/Infinix/Itel phone gets the real number; scale it to 2,000.
- To leave room, harmless messages (the vast majority) now skip extra work: no sorting of explanation data and no fingerprint unless the message is flagged. A test fails if scoring 2,000 messages ever takes more than 15 seconds on the build machine.

## The Report button (`features/reporting/ManualReport.kt`, warning screen)

### What it is
On a warning screen, **"Report this number"** lets the person tell the Linda radar about a scammer by hand, so others can be warned. Automatic reporting (for people who agreed on the consent screen) already exists; this is for choosing to report one specific message, including when automatic reporting is off, or for a CAUTION-level message Linda would not report by itself.

### Privacy: the person sees exactly what is sent, every time
Tapping the button does not send anything. It opens a confirmation listing the fields: the sender's number, the type of scam, how sure Linda is, the scrambled fingerprint of the message ("the message cannot be read from it") and the anonymous device code, followed by "Never sent: the message itself, your contacts, or your own phone number". Only **Send report** queues it. That confirmation is the person's consent for *this one report*, so it works even if the global reporting switch is off, and it can never send more than the automatic report does: the same `ReportPayload` with the same seven fields (a test checks the keys, and that no text field exists to leak).

### Rules (unit tested in `ManualReport`)
- Allowed for SCAM and CAUTION messages from a real phone number.
- **Never** for verified senders (M-Pesa, banks, KPLC, KRA), never for pasted text or a sender name with no number, and never twice for the same message: the button turns into "Reported. Thank you for helping protect others."
- Hidden if the person marked the sender as safe.
- If the message has no fingerprint, no report is built, so no junk is sent.

### How it is sent
Through the same offline queue as automatic reports: saved first, sent when the phone is online, retried with growing delays. If no radar address is set yet, the dialog says so and the report waits. Automatic reports mark the message as "reported" too, so the button never offers a duplicate.

### Consent withdrawn
If the person later switches automatic reporting off, any **automatic** reports still waiting in the queue are **deleted, not sent**. Reports they confirmed one by one stay, because those were their own explicit decision.

## The download page (`site/`, `docs/DOWNLOAD.md`)

### What it is and why it is built this way
A single HTML page, hosted free on GitHub Pages, where a person downloads and installs Linda. We chose a plain page with no framework, no libraries and no outside fonts or scripts: it loads fast on a cheap phone and a slow connection, cannot be broken by someone else's website going down, and a test fails if it ever loads anything from another site. The only outside call is to GitHub's API, to show when the newest build was made; if that fails the download button still works, because it is an ordinary link.

### The download button never goes stale
Every build deletes and recreates the `latest-build` release with the same file name, so one link always means "the newest app". The page therefore never needs editing when the app changes.

### Honest by design
It says plainly that Linda is a hackathon prototype outside the Play Store (which is why Android warns), lists every permission with its reason, and states that message text is never sent. A test compares the permission table with the app's real manifest, so adding a permission without explaining it on the page fails the build.

### Fix made at the same time
The APK was published only from a branch literally named `main`, so merging into our default branch left a stale download. It now publishes from whichever branch is the repository's default.

### Honest limits
We have not seen it served from github.io yet (Pages needs the owner to switch it on). The repository name ends in a dot, which is unusual and could confuse some links. It cannot count downloads.

## UI redesign, step 1: the design foundation (`android/.../core/ui/theme`, `core/ui/components`)

### What changed and why
The app used a dark cyan-and-pink look. We replaced it with the design in `docs/design-system.md`: Safaricom green, light by default (readable in sunlight), dark mode that follows the phone's setting, Poppins for headings and Inter for text. Nothing about detection changed. This step only builds the "kit" the other screens will use, and every existing screen picks up the new colours and fonts automatically.

### The kit
- **Palette.kt**: every colour as a plain number. **Color.kt**: the named tokens (`green500`, `scamRed`, `layerL`...) plus `LindaTheme.colors`, which gives the right shade for light or dark.
- **Type.kt, Shape.kt, Spacing.kt, Motion.kt**: the size table, the corner radii (8/16/24dp), the only allowed spacings, the animation timings, and the "shield notch" shape (a card with its bottom-right corner cut off like a shield point).
- **Components**: `LindaButton` (52dp, one primary per screen), `LindaCard` (soft green shadow in light mode), `LevelChip` and `RiskIcon` (icon plus words plus colour, never colour alone).

### A deliberate difference from the design file
Two colour pairs written in the design file fail its own 4.5:1 reading rule: green `#00A651` words on the mint Safe background (2.9:1) and red `#E4002B` words on the pink Scam background (4.1:1). So *words* use deep green `#007A3D` and a slightly deeper red `#D0002A`, while icons, borders and big fills keep the exact brand colours. A unit test (`PaletteContrastTest`) checks every text pair in light and dark mode, so a future colour change that makes text hard to read fails the build.

### Fonts
Poppins and Inter are bundled inside the app (both free under the SIL Open Font License; listed in `LIBRARIES.md`), so nothing is downloaded and the app still works offline. They add about 1.5 MB to the APK.

### Not done yet (later steps)
The Layer Trace and scan animation (step 2), the home ring and verdict card (step 3), restyling the other screens (step 4), and the demo overlay (step 5). Until then the old screens look green and clean but keep their old layouts. Not yet seen on a real phone.

## UI redesign, step 2: the Layer Trace and the scan sweep (`android/.../features/trace`)

### What it is
Linda checks every message in five steps, and the name spells them: **L**anguage, **I**ntelligence, **N**etwork, **D**ecision, **A**lert. The Layer Trace is a row of five boxes, one per letter. When a message has been analysed they light up from left to right in under half a second (480 ms), so a judge can *see* the steps happen. A layer that found something risky takes the verdict's colour (amber or red) and gives a small shake; a layer that found nothing stays green with a tick. Tap a letter to read what that layer found. With the phone's "remove animations" setting on, the boxes just appear finished.

### It only tells the truth
Each line comes from something Linda really worked out for that message, never a made-up figure:
- **L**: words the scammer disguised and how Linda read them (for example "M-P3SA" became "mpesa"). Amounts like "Ksh3,140" are not treated as disguises.
- **I**: the type of scam, the scam wording the on-device model leaned on, and how sure it was.
- **N**: who sent it: an ordinary phone number, not in your contacts, first message, a link, pretending to be M-Pesa, or a number other Linda phones already confirmed. A verified sender (M-Pesa, bank, KPLC, KRA) is shown as verified and never flagged.
- **D**: the verdict.
- **A**: what Linda did: warned you, reported the number, alerted your guardian.
Pasted text has no sender, and the trace says so instead of guessing.

### Where it shows
On the warning screen (when you open a saved warning) and in the "Is this a scam?" checker, where the phone also buzzes: nothing for a safe message, one tick for Caution, two pulses for Scam. The buzz uses the phone's own touch-feedback setting, so it needs no extra permission and stays quiet if the person turned that off.

### How it is tested
The logic that decides which layer flags what (and the 480 ms timing, and the buzz pattern) is plain Kotlin with 17 unit tests, including: a verified sender is never flagged, amounts are not "disguises", and the sweep always finishes under 600 ms. The drawing itself is not yet seen on a real phone.

## UI redesign, step 3: home ring, verdict card, full-screen scam alert

### The home ring (`features/home`)
The home screen is now a ring of five arcs, one for each layer (L I N D A), around the Linda mark (a shield cut into five coloured segments, which is also the new app icon). All five bright means "LINDA is protecting you." If something is switched off, only the arc that needs it fades, and one button fixes it:
- **L** needs permission to receive texts, **N** needs contacts access, **A** needs notifications on. **I** (the model) and **D** (the decision) live inside the app, so they never fade.
- The fix button asks for the permission; if the phone will not ask again, it opens the app's settings page. The check runs again every time you come back to the app.
- Below the ring: how many scams were stopped this month in big numbers, or "No scams yet. LINDA is watching." The ring "breathes" (grows 2%) once every 6 seconds, unless the phone's remove-animations setting is on. The card has the "shield notch", a bottom-right corner cut like a shield point, used only here.
The rules for which arc fades are plain code with their own unit tests.

### The verdict card (`features/detail`)
Opening a warning now shows one card tinted for its risk (amber for Caution, red for Scam, green for Safe) with a bar of that colour on its left edge: icon and words, a three-line preview of the message, the Layer Trace, the reasons as separate lines, and the buttons. Only Scam cards glow red. There is **one main button**: "Don't send money" for a Scam (or "I'll be careful" for Caution), which closes the screen. Under it, smaller: "I already sent money" (opens Recovery), "Mark as safe" and "Report this number". A message that imitates M-PESA but did not come from M-PESA also gets a crossed-out-receipt badge: "Not from M-PESA. You have not received any money."

### The full-screen scam alert (`ScamTakeover`)
When you tap a warning notification for a confident scam, the first thing you see is a big alert: a large octagon, "Stop. This looks like a scam.", the top reason, two short buzzes, and two buttons ("OK, show me why" and "I already sent money"). If voice warnings are on, it reads **exactly** the headline and the reason shown on screen; a unit test checks that the spoken headline equals the text in `strings.xml` in both languages. It does not repeat the voice if the text just arrived and was already read aloud (30-second rule). Opening an old warning from History goes straight to the verdict card, so it does not shout every time. The alert fills the content area; the bottom navigation bar is still visible under it.

### Not done yet
Restyling History, Inbox, Checker, Recovery, Guardian, Settings, Onboarding and Demo (step 4); the demo overlay (step 5). Not yet seen on a real phone.

## UI redesign, step 4: every other screen in the new look

### What changed
History, Inbox scan, the "Is this a scam?" checker, Recovery, Family Guardian, Settings, Onboarding, Demo and the bottom bar now use the same kit as Home and the warning screen: one button style (`LindaButton`: a filled deep-green main button, outlined secondary buttons), one card style with a soft green shadow, one text-field style (raised fill, green focus border), the shared type sizes and spacing. The leftover purple from Material's default colours is gone, so filter chips, switches and the selected bottom-bar item are all green.

### Screens with real design changes
- **Checker**: the answer is now the same Verdict Card as on the warning screen (icon and words, the five-layer trace, the reasons), for safe messages too, with a short note when the message looks fine.
- **Recovery**: the checklist is a vertical stepper. Each step is a numbered dot on a line; ticking a step turns its dot into a tick; the first unticked step has a ring that pulses gently (still when animations are off). If you sent the money in the last day, the top shows "Don't panic. Let's act fast." and a chip "Reversals work best within minutes." The "which step is current" rule is plain code with a test. The numbers and phone codes in the steps did not change and are still marked "verify before demo" in `RecoveryConfig.kt`.
- **Family Guardian**: the log of alerts that were sent shows each one as an amber Caution-style card, so the protected person sees what their guardian was told. The design file describes a card on the *guardian's* phone; we did not build that because the guardian receives a plain text message, not the app.
- **Onboarding** starts with the Linda mark. **Bottom bar** uses the green selected indicator.

### Rules we kept and where we bent them
- Red is only for verdicts. Messages like "permission was denied" are now normal dark text, not red. The one exception is the code field on the Recovery form, which still shows Material's red outline when the transaction code is invalid.
- Every spacing now uses the 4/8/12/16/24/32 scale; the old 14, 10, 6 and 20 dp values were moved to the nearest allowed one.
- Text fields keep Material's 56 dp minimum height (the design file says 52 dp) so they stay easy to tap.
- Some screens still have more than one filled main button (Onboarding has "Allow" and "Done", Settings has a few switches' worth of actions). Those are setup screens, not warnings.

### Not done yet
The demo overlay (step 5). Not yet seen on a real phone, and the Swahili for the new lines is unreviewed.

## UI redesign, step 5: the demo overlay for judges (`android/.../features/demo`)

### What it is
A hidden panel for the demo. **Tap the Linda mark in the middle of the home ring seven times** (each tap within 1.5 seconds of the last) and a translucent panel slides up above the bottom bar; the same tap sequence turns it off again. (It can also be switched in Settings, in the developer section that opens when you tap the version number seven times.) Every time Linda analyses a message (a real text, a demo-mode message, or text pasted into the checker) the panel plays the five-layer sweep again and, under it, prints what each layer actually produced, in a monospaced font:
- **L**: the cleaned-up text Linda read, and any disguised words it undid (for example `M-P3SA->mpesa`).
- **I**: the score next to the two warning lines (`score=0.94 warn=0.55 scam=0.80`), the scam type and the wording the model leaned on.
- **N**: the sender and the facts used: in your contacts, first message, verified sender, ordinary phone number, link, M-Pesa-style, fake M-Pesa, on the confirmed list.
- **D**: the verdict and score.
- **A**: what Linda did: `notified`, `voiced`, `guardian:sent` (or why it was skipped), `reported:queued`.
It can be folded to a one-line bar, or hidden with Hide.

### Privacy
The overlay is **off by default**. While it is off, Linda keeps nothing about any message in memory for it. While it is on, the last analysed message's cleaned-up text is held in memory only so the panel can show it; it is never saved, never sent anywhere, and switching the overlay off throws it away. It only ever shows what is on this phone's own screen, so show it only on the demo phone. The key=value lines are log-style on purpose and are not translated; the title, the Hide and Fold buttons and the Layer Trace itself are in English and Kiswahili.

### How it is tested
The tap sequence rule (seven quick taps, a pause restarts it, slow tapping never triggers it) and the text of every line are plain code with 8 unit tests.

### With this step the redesign is complete
Foundation, Layer Trace and scan sweep, home ring, verdict card, full-screen alert, every other screen and the demo overlay are built and tested on GitHub's build. None of it has been seen on a real phone yet.

## Motion polish (apple-design skill), part A: the Android app

### What changed
We read the third-party `apple-design` guidance (kept in `.claude/skills/apple-design/`, MIT) and applied the parts that fit a phone app, without adding any new "show-off" animation:
- **Springs instead of fixed-time animations.** Anything you touch now settles with a *critically damped spring* (no bounce): buttons shrink to 97% the instant you press and settle back; tappable cards do the same. A spring starts from wherever the thing currently is, so tapping again mid-motion never jumps. The numbers come from Apple's two designer-friendly settings (damping 1.0, response 0.35 seconds), converted to Compose's stiffness by one formula (`SpringMath`, unit tested).
- **The verdict card now rises 16 dp and fades in** when a warning opens or a checker result appears (this was in the design file but had never been wired up). With "remove animations" on, it only fades.
- **The buzz now matches the sweep.** Before, the phone buzzed after the whole 480 ms sweep. Now it buzzes the moment the first flagged layer lands, so what you see and what you feel happen together. A safe message never buzzes.
- **The demo overlay folds and unfolds with a spring** (instantly when animations are off).
- **Tighter letter spacing on big titles** (the larger the text, the tighter; body text unchanged), as Apple does.

### What we deliberately did not do
Dragging, flicking, rubber-banding and momentum: nothing in Linda is draggable, and inventing gestures would add things to explain and break. We also kept Poppins/Inter instead of the system font, and the design file's rule that the scan sweep is the only show-off animation.

### How to judge it
Spring feel can only be judged by touching a real phone. If it feels too slow or too quick, there is one number to change: `SpringMath.CALM_RESPONSE` (smaller = quicker).

## Motion polish (apple-design skill), part B: the dashboard and the download page

- **Less motion on request.** People whose device says "reduce motion" no longer get the pulsing dots and colour fades on the dashboard map (the colours still change, so nothing is lost), and the download page and dashboard buttons stop shrinking when pressed.
- **Instant press feedback.** The dashboard's control buttons and the page's Download and language buttons shrink to 97% the moment a finger or mouse goes down (100 ms), not when it is released.
- **Tighter type where it is big.** The page headline and the dashboard's big numbers get slightly tighter letter spacing; small text is unchanged.
- **Guarded by tests.** The page tests (now 13) fail if the page ever gains a transition or animation without a reduced-motion rule, and if the Download button loses its press feedback. The dashboard still passes its type check, its 13 tests and its production build.
