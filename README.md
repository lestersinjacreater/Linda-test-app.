# Linda: On-Device Scam Shield

Linda (Swahili for "protect") is an Android app that detects mobile-money scams on the phone, offline and in real time,
and warns the user with a clear reason in English or Swahili. Entry for the Safaricom interns' *Intelligence Unleashed* hackathon (Finance track).

- Project spec and rules: [`CLAUDE.md`](CLAUDE.md)
- Plain-English explanation of every module: [`docs/EXPLAINED.md`](docs/EXPLAINED.md)
- Architecture: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Evaluation and device checklist: [`docs/EVALUATION.md`](docs/EVALUATION.md)
- Declared libraries: [`LIBRARIES.md`](LIBRARIES.md)

## Get the app

APKs are built by GitHub Actions. Open the **Releases** page, download `linda-debug.apk` from the `latest-build` pre-release, and install it on your phone.

## Layout

| Folder | What is in it |
|---|---|
| `android/` | The Android app (Kotlin, Jetpack Compose) |
| `ml/` | Python: data, training, evaluation, model export |
| `shared/` | Test vectors used by both the Python and Kotlin tests |
| `docs/` | Explanations, architecture, evaluation |
| `dashboard/` | Campaign radar (Phase 3, not started) |
