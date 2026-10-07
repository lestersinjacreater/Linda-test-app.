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
| JUnit | 4.13.2 | EPL 1.0 | JVM unit tests (test-only, not shipped in the APK). |

Not used, on purpose: Firebase, analytics, ads or tracking SDKs, and any network library (the core app works fully offline).

## ML (Python 3.11)

| Library | Version | Licence | Why we use it |
|---|---|---|---|
| pytest | 8.3.4 | MIT | Runs the Python tests. |

pandas, numpy and scikit-learn are added here when the training code (F3) lands.

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

Planned (not added yet): Next.js, React, Tailwind (dashboard); simulator libraries.
Africa's Talking and Daraja are **stubbed** for now (no SDK, no credentials).
