# SpendTracker

SpendTracker is an Android-first, private expense tracker that recognizes
financial transactions from SMS, categorizes them, and summarizes weekly and
monthly spending. The MVP runs entirely on the phone: no login, backend, or
network connection is required.

## Project status

The repository currently contains a working feasibility build:

- Kotlin Multiplatform transaction models and deterministic parser.
- Native Android UI with Jetpack Compose and Material 3.
- Onboarding plus Dashboard, Transactions, and Settings navigation.
- Immutable ViewModel-driven UI state with constructor-injected platform adapters.
- Debug-only synthetic data mode and Compose previews for private UI development.
- Explicit `READ_SMS` permission flow.
- Streaming scan of the previous three calendar months.
- In-memory summary for recognized INR spend and foreign transactions.
- No raw-message persistence and no `INTERNET` permission.

Local persistence, transaction editing, weekly/monthly reporting, and live
ingestion of new messages are planned but not implemented. See
[`docs/STATUS.md`](docs/STATUS.md) for the verified baseline.

## Documentation

Start at [`docs/README.md`](docs/README.md). The executable MVP sequence is in
[`docs/MVP_PLAN.md`](docs/MVP_PLAN.md), and coding agents must read
[`AGENTS.md`](AGENTS.md) before changing the project.

## Project layout

- `shared`: portable models, import rules, parser, and host-side tests.
- `androidApp`: Android permissions, SMS provider access, and Compose UI.
- `docs`: product, architecture, delivery, testing, privacy, and handoff context.

## Quick start

Prerequisites:

- Android Studio compatible with Android Gradle Plugin 9.4.
- Android SDK Platform 37.
- JDK 17 or a newer version supported by the configured Gradle/AGP versions.

Verify the project:

```shell
./gradlew :shared:jvmTest :androidApp:testDebugUnitTest
./gradlew :androidApp:lintDebug :androidApp:assembleDebug
```

Open this folder in Android Studio, select an Android emulator or device, and
run the `androidApp` configuration. Detailed setup is in
[`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md).

## Privacy warning

SMS data and financial data are sensitive. Never log or persist raw SMS bodies,
and never add real user messages or identifiers to fixtures. The complete rules
are in [`docs/DATA_AND_PRIVACY.md`](docs/DATA_AND_PRIVACY.md).
