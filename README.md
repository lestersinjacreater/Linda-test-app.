# Linda

Linda spots M-Pesa scams the moment they arrive and protects people on every kind of phone.
Entry for the Safaricom interns' *Intelligence Unleashed* hackathon (Finance track).

> Kaa Rada stops the payment. Linda stops the conversation.

- Build spec (start here): [`CLAUDE.md`](CLAUDE.md), plus one `CLAUDE.md` per component folder
- Plain-English explanation of every part: [`docs/EXPLAINED.md`](docs/EXPLAINED.md)
- Declared libraries: [`LIBRARIES.md`](LIBRARIES.md) · datasets: [`DATASETS.md`](DATASETS.md)

## Get the app

APKs are built by GitHub Actions. Open **Releases**, download `linda-debug.apk` from the `latest-build` pre-release, and install it.

## Layout

| Folder | What is in it | Status |
|---|---|---|
| `android/` | Kotlin app: on-device detection, reporting, call screening | skeleton |
| `ml/` | Dataset pipeline, training, evaluation, model export | scaffold |
| `backend/` | The radar: reports, confirmation, lookups, USSD, payment precheck | not started |
| `simulator/` | Mock telco for the demo (separable) | not started |
| `dashboard/` | Live demo dashboard | not started |
| `shared/` | Contracts, test vectors, verified senders, model artifacts | partial |
