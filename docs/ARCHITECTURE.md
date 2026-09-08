# Architecture

## Framework decision

Use Kotlin Multiplatform (KMP) for portable domain and data logic, with native
platform UI and platform integrations:

- Android: Kotlin, Jetpack Compose, Material 3, Android SMS APIs.
- Shared: KMP common code for models, parser, categorizer, aggregations, use
  cases, repository contracts, and Room KMP persistence.
- Future iOS: SwiftUI shell consuming the shared core where useful.

This preserves native Android access and UI while avoiding a dead end for
portable business rules. It does not imply feature parity on iOS. Apple's
public message-filter extension is for classifying newly received SMS/MMS from
unknown senders, does not cover iMessage or known contacts, and cannot write to
the containing app's shared container. A future iOS product therefore needs a
different input such as user-imported statements or a consented financial-data
integration. See Apple's [SMS and MMS Message Filtering documentation](https://developer.apple.com/documentation/identitylookup/sms-and-mms-message-filtering).

## Dependency direction

```text
Android Compose UI
        |
Android ViewModels / lifecycle orchestration
        |
Shared use cases and observable repository contracts
        |
  +-----+------------------+
  |                        |
Room/SQLite repository     Android SmsSource adapter
  |                        |
Stored parsed data         Android SMS content provider / receiver
        \                  /
         parser -> categorizer -> inclusion policy
```

Dependencies point inward toward shared domain behavior. The UI must not query
the content provider or database directly. The parser must not depend on Android
classes, wall-clock globals, or Compose.

## Current modules

### `shared`

Currently contains:

- `ImportPolicy`
- `SourceMessage`, `Money`, `ParsedTransaction`, and enums
- `FinancialMessageParser` and explicit accepted/review/rejected outcomes
- versioned `TransactionCategorizer`, `MerchantNormalizer`, and merchant-rule model
- `MessageSource` platform-boundary contract
- `TransactionRepository` observable read/upsert/edit/delete contract
- `ImportCoordinator`, durable `ImportState`, and import repository contracts
- Room 3 KMP entities, DAOs, database, repositories, version-3 schema, and migrations
- `PeriodCalculator` and `ReportAggregator` for local day, Monday-start week,
  and local-month windows; historical offsets; comparison ranges; category sums;
  and daily series with an injected clock and time zone
- Android, JVM, and iOS database builders using bundled SQLite
- portable host-side tests

Target additions:

- inclusion policy
- additional controlled parser-template expansion as real coverage evidence grows

### `androidApp`

Currently contains:

- thin `MainActivity` composition/permission boundary
- Compose app shell with onboarding, dashboard, transactions, and settings
- immutable app/screen state and constructor-injected `AppViewModel`
- lazy ledger rows, combined filters, and parsed transaction detail/editor
- repository-backed category/inclusion overrides and explicit reset controls
- day/week/month dashboard with historical previous/next navigation, comparison,
  category breakdown, daily series, and deep links into filtered transactions
- light/dark Material theme, resources, reusable components, and previews
- runtime `READ_SMS` permission request
- `SmsInboxReader` implementing the shared `MessageSource` boundary
- `ImportCoordinator` streaming source rows through parser, fingerprinting, and bounded upserts
- complete permission states, cancellable progress, retry, and settings recovery
- foreground reconciliation based on the last successful scan
- application-scoped Room repository for production data
- Android Keystore-backed keyed source fingerprints
- `SmsSourceLookup` resolving the persisted provider row ID into an ephemeral
  on-demand source-message dialog that never persists or logs the body
- debug-only synthetic repository/data and a release variant with no demo source
- ViewModel unit tests and connected Compose navigation/state tests

Target additions:

- receiver plus reconciliation scheduling
- expanded screen-specific Compose/instrumentation tests

## Target package shape

The exact names can evolve, but dependency boundaries should follow this shape:

```text
shared/.../core/
  model/
  parser/
  categorization/
  aggregation/
  importing/
  repository/
  usecase/
  database/             # if Room KMP is kept in shared

androidApp/.../app/
  data/sms/
  data/database/        # only Android-specific Room setup if needed
  ui/navigation/
  ui/onboarding/
  ui/dashboard/
  ui/transactions/
  ui/settings/
  worker/
```

## State and UI

- Use a single activity and Compose navigation.
- Each feature screen receives immutable state and emits user intents.
- ViewModels expose `StateFlow`; Compose collects it with lifecycle awareness.
- Keep composables previewable by passing data and callbacks rather than
  resolving Android services inside the UI.
- Use one repository as the source of truth after persistence exists. UI totals
  must be derived from stored transactions, not the latest scan callback.
- Provide a debug-only synthetic data source so UI can be developed and tested
  without access to a person's SMS inbox.

MVP-06 filters the observed parsed ledger with shared `TransactionFilter` rules;
rows are composed lazily, but database paging is not implemented. Date inputs
use ISO calendar dates in the device time zone, with an inclusive through-date
converted to an exclusive next-day boundary. Detail selection remains independent
of filters so a saved correction cannot unexpectedly dismiss the editor.

## Import pipeline

History and live messages must share the same pipeline:

1. Platform adapter streams a minimal ephemeral `SourceMessage`.
2. A source identity/fingerprint is computed before the body is discarded.
3. Parser returns `Accepted`, `NeedsReview`, or `Rejected`; accepted/reviewable
   outcomes carry normalized fields and rejected outcomes carry only a safe reason.
4. Categorizer and inclusion policy assign detected defaults.
5. Repository performs an idempotent upsert in a transaction.
6. Existing user overrides are preserved during reparsing.
7. Observed repository queries update the UI.

The receiver should do minimal work and enqueue durable processing where Android
lifecycle constraints require it. Foreground reconciliation is mandatory because
a broadcast can be missed or delayed.

## Persistence

Room 3 KMP over bundled SQLite is the local store. Schema, queries, and repository
logic are shared; Android, JVM, and iOS source sets construct the platform path.
Follow the official [Room KMP setup](https://developer.android.com/kotlin/multiplatform/room).

Schema details and uniqueness rules are in `DATA_AND_PRIVACY.md`. Database access
uses repository interfaces, transactions for import/upsert, and observable
queries for screens. Every migration requires a test.

## Time and money

- Persist source timestamps as epoch milliseconds.
- Aggregate using the device's current local time zone at query/use-case time.
- Define week as Monday 00:00 through the next Monday 00:00 for the India-first
  MVP. Make it explicit and unit-tested rather than relying on locale defaults.
- Define month as local calendar month.
- Store amount as non-negative `Long` minor units plus ISO-like currency enum.
- Store direction separately; do not encode debit as a negative amount.

## Dependency injection

Start with constructor injection and a small application-level composition root.
Do not add a DI framework until object-graph complexity demonstrates a need.
Tests must be able to replace clock, SMS source, repository, and schedulers.

## Backend evolution

Future authentication/sync must sit behind repository and sync interfaces and
remain opt-in. It must not make the local database a cache that stops working
offline. Adding a backend requires a new privacy/security design and is not an
MVP implementation detail.
