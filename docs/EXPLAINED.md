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
