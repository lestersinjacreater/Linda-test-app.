# Declared open-source libraries

Required by the hackathon rules. Update this file **whenever a dependency is added**.

## Android app

| Library | Version | Licence | Why we use it |
|---|---|---|---|
| Kotlin (stdlib, compiler plugin) | 2.0.21 | Apache 2.0 | The app language. |
| Android Gradle Plugin | 8.7.3 | Apache 2.0 | Builds the APK. |
| Jetpack Compose (BOM 2024.12.01): `ui`, `material3` | BOM 2024.12.01 | Apache 2.0 | The UI toolkit, with Material 3 components. |
| Kotlin Compose compiler plugin | 2.0.21 | Apache 2.0 | Required to compile Compose code with Kotlin 2.0. |
| AndroidX Activity Compose | 1.9.3 | Apache 2.0 | Hosts Compose in the activity; edge-to-edge support. |
| AndroidX Navigation Compose | 2.8.5 | Apache 2.0 | Moves between the Home, Check, History and Settings screens. |
| AndroidX Lifecycle (ViewModel Compose, Runtime Compose) | 2.8.7 | Apache 2.0 | View models, and collecting state safely on screen. |
| AndroidX Core KTX | 1.15.0 | Apache 2.0 | Kotlin helpers for Android APIs. |
| AndroidX Room (runtime, ktx, compiler) | 2.6.1 | Apache 2.0 | The on-device database for detection history. |
| KSP (Kotlin Symbol Processing) | 2.0.21-1.0.28 | Apache 2.0 | Runs Room's code generator. |
| AndroidX WorkManager (work-runtime-ktx) | 2.9.1 | Apache 2.0 | Retries queued scam reports with growing delays when the phone is offline, and runs the 15-minute blocklist sync. |
| JUnit | 4.13.2 | EPL 1.0 | JVM unit tests (test-only, not shipped in the APK). |
| org.json | 20240303 | JSON licence (public-domain style) | JVM unit tests only: Android ships its own org.json, but its JVM-test stub does nothing. |

Not used, on purpose: Firebase, analytics, ads or tracking SDKs, and any network library (the app uses the platform's own `HttpURLConnection`, only for reports without text and the blocklist; the core app works fully offline).

## ML (Python 3.11)

| Library | Version | Licence | Why we use it |
|---|---|---|---|
| pytest | 8.3.4 | MIT | Runs the Python tests. |

| scikit-learn | 1.5.2 | BSD-3 | `ml/` | Trains the TF-IDF + logistic regression baseline. The app does not ship it: only the exported JSON ships. |
| NumPy | 2.1.3 | BSD-3 | `ml/` | Arrays for scikit-learn. |
| SciPy | 1.14.1 | BSD-3 | `ml/` | Sparse matrices that join text features and context features. |

## Build and CI

| Tool | Version | Licence | Why |
|---|---|---|---|
| Gradle | 8.9 | Apache 2.0 | Build tool (via the Gradle wrapper). |
| GitHub Actions: `checkout`, `setup-java`, `setup-python`, `setup-gradle`, `upload-artifact` | v4/v5 | MIT | CI that builds the APK and runs the tests. |

## Backend, simulator, dashboard

| Library | Version | Licence | Used in | Why |
|---|---|---|---|---|
| FastAPI | 0.115.6 | MIT | backend | The web framework for the radar endpoints. |
| Uvicorn | 0.34.0 | BSD-3 | backend | Runs the FastAPI app. |
| SQLAlchemy | 2.0.36 | MIT | backend | Talks to SQLite (dev/tests) and Postgres (docker) with the same code. |
| psycopg2-binary | 2.9.10 | LGPL-3.0 (with exceptions) | backend | Postgres driver, used only in docker-compose. |
| httpx | 0.28.1 | BSD-3 | backend | Calls the telco `/network/confirm` endpoint. |
| Pydantic | 2.12.5 | MIT | backend | Validates report fields and rejects unknown ones (so message text cannot slip in). |
| pytest | 8.3.4 | MIT | backend | Tests. |

Simulator (`simulator/`) uses FastAPI, Uvicorn, httpx and Pydantic at the same versions as above, plus:

| Library | Version | Licence | Why |
|---|---|---|---|
| websockets | 14.1 | BSD-3 | Lets Uvicorn serve the `/events` WebSocket the dashboard listens to. |
| pytest-asyncio | 0.25.0 | Apache 2.0 | Runs the simulator's async tests (test-only). |

Planned (not added yet): Next.js, React, Tailwind (dashboard).
Africa's Talking and Daraja are **stubbed** for now (no SDK, no credentials).
