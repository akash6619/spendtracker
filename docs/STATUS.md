# Current status

- Last updated: 2026-09-05
- Current milestone: MVP-04 detection and parser hardening complete
- Next recommended slice: MVP-05 categorization and override rules
- Active work: None

## Active work

Agents must claim work here before implementation and clear the row at handoff.

| Slice | Agent/task | Files or area | Started | Notes |
| --- | --- | --- | --- | --- |
| None | — | — | — | MVP-04 review fixes complete; MVP-05 is ready |

## Implemented now

- Two Gradle modules: `androidApp` and `shared`.
- KMP targets for Android, JVM host tests, iOS device, and iOS simulator.
- Single-activity Compose/Material 3 app shell with separate onboarding,
  dashboard, transactions, settings, theme, format, and common-component files.
- Bottom navigation among Dashboard, Transactions, and Settings.
- Immutable `AppUiState` driven by a constructor-injected `AppViewModel`.
- Shared `MessageSource` and durable `TransactionRepository` contracts.
- Room 3 KMP database with bundled SQLite and Android, JVM, and iOS builders.
- Version-2 schema for transactions, import state, settings, and merchant rules,
  with exported JSON fixtures and a tested version-1-to-version-2 migration.
- Chronological, detail, period, category-total, review, and count DAO queries.
- Transactional provider-ID/fingerprint upsert with provider-reuse handling,
  concurrent deduplication, and user-override preservation.
- Android SMS fingerprints use an installation-local HMAC-SHA256 key held in
  Android Keystore; raw bodies are discarded after parsing and fingerprinting.
- Application-scoped database/repository construction and a full local-reset
  operation that clears stored facts before deleting the fingerprint key.
- Shared `ImportCoordinator` streams one message at a time and persists accepted
  records in bounded, idempotent batches of 100.
- Durable initial/reconciliation import states with attempt/success timestamps,
  safe progress counts, coarse failure codes, cancellation, and retry.
- Exact injected three-calendar-month initial cutoff plus five-minute overlap
  reconciliation after the last successful scan.
- Complete permission UI for not requested, denied, permanently denied, granted,
  and revoked states, with retry or app-settings recovery.
- Startup converts an abandoned durable `RUNNING` state to retryable
  `FAILED/INTERRUPTED`, preventing process death from trapping the scan controls.
- A null SMS-provider cursor is treated as a source failure rather than a
  successful empty inbox.
- Debug-only synthetic repository with five safe sample records; release builds
  cannot construct it.
- Empty, loading, content, and error previews for primary screens.
- Android strings/plurals resources and a minimal light/dark Material theme.
- Runtime `READ_SMS` permission request only after an explicit privacy disclosure
  and user action.
- `SmsInboxReader` streaming inbox rows from an explicit cutoff on IO.
- Portable transaction/money/category models.
- Deterministic parsing for initial INR, USD, EUR, GBP, and JPY patterns.
- Version-2 parser outcomes explicitly distinguish accepted, needs-review, and
  rejected messages with privacy-safe reason enums.
- India-first synthetic coverage includes card/bank debit, UPI purchase, fee,
  refund/reversal, credit, transfer, and ATM withdrawal wording.
- Exact amount parsing supports INR aliases, Indian and international grouping,
  supported foreign currencies, malformed precision, and overflow rejection.
- Attempt-local aggregate rejection-reason counts provide safe diagnostics; no
  source content or identifiers are included or added to the Room v2 schema.
- Conflicting amount/direction records remain visible but default to excluded
  from spend, and that detected inclusion survives Room round-trips.
- Amount matching stops before whitespace-separated dates or references instead
  of merging their digits into the transaction amount.
- Initial kind, direction, merchant, account hint, category, and confidence rules.
- Credits/refunds/transfers/ATM withdrawals excluded from spend by policy.
- Persisted results feeding a dashboard summary and chronological transaction list.
- No raw SMS persistence, login, backend, or internet permission.
- Nineteen shared JVM tests, eight Android local tests, four connected Compose
  tests, two connected importer/provider tests, and one connected Keystore test.
- Minimal code comments document privacy boundaries, exact-money conversion,
  deduplication collisions, override precedence, and repository switching.
- Production classes, interfaces, enums, and state holders have explanatory KDoc
  covering responsibility, current behavior, data flow, and important boundaries.
- `AGENTS.md` and the development guide require the same KDoc and focused-comment
  standard for all future production code.
- Git repository initialized on `main` and linked to the private GitHub repository
  `akash6619/spendtracker`.

## Verified baseline

On 2026-09-04, the MVP-01 final gate completed successfully:

```shell
./gradlew :shared:jvmTest :androidApp:testDebugUnitTest \
  :androidApp:lintDebug :androidApp:assembleDebug \
  :androidApp:assembleRelease :androidApp:connectedDebugAndroidTest
```

Result: `BUILD SUCCESSFUL`; 7 shared tests, 3 Android ViewModel tests, and 2
connected Compose tests passed. Android lint passed, and debug and release APKs
assembled. The Compose tests ran on `SpendTracker_API_37` (API 37).

The updated onboarding, debug transaction list, actual scanned transaction list,
dashboard, and bottom navigation were visually inspected on the same emulator.
This AVD name is local context, not a repository dependency.

On 2026-09-04, the MVP-02 gate also completed successfully:

```shell
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:jvmTest \
  :androidApp:testDebugUnitTest :androidApp:lintDebug \
  :androidApp:assembleDebug :androidApp:assembleRelease
./gradlew :androidApp:connectedDebugAndroidTest
```

Room persistence, close/reopen behavior, provider/fingerprint collisions,
concurrent idempotency, override preservation, reporting queries, deletion, and
the Android Keystore fingerprint were tested. Connected tests passed on
`SpendTracker_API_37` (API 37). The iOS simulator shared source compiled.
An app-level smoke test scanned three synthetic emulator SMS records, force-stopped
and relaunched the app, then rescanned. The dashboard remained at two included
INR transactions totaling ₹1,749.50 plus one foreign transaction, confirming
restart persistence and cross-restart deduplication.

On 2026-09-05, the code-comment clarity pass completed successfully:

```shell
./gradlew :shared:jvmTest :androidApp:testDebugUnitTest \
  :androidApp:lintDebug :androidApp:assembleDebug
```

On 2026-09-05, the MVP-03 gate completed successfully:

```shell
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:jvmTest \
  :androidApp:testDebugUnitTest :androidApp:lintDebug \
  :androidApp:assembleDebug :androidApp:assembleRelease
./gradlew :androidApp:connectedDebugAndroidTest
```

The shared suite covered exact cutoffs, safe progress, cancellation, partial-
failure retry, overlap reconciliation, 10,000-message bounded batching, durable
Room state, and the version-1-to-version-2 migration. Connected tests passed on
`SpendTracker_API_37` (API 37), including a synthetic source through the real
Android Room database.

Manual emulator checks covered fresh disclosure, first denial and retry, second
denial and app-settings recovery, grant and successful import, process restart,
foreground reconciliation, and permission revocation. The imported ledger
remained at two included INR records totaling ₹1,749.50 plus one excluded foreign
record after restart; reconciliation added no duplicates.

The subsequent review-fix pass added regression coverage for process death with
a persisted `RUNNING` state and for an unavailable/null SMS-provider cursor. The
same full build gate and seven connected tests passed on the API 37 emulator.

On 2026-09-05, the MVP-04 gate completed successfully:

```shell
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:jvmTest \
  :androidApp:testDebugUnitTest :androidApp:lintDebug \
  :androidApp:assembleDebug :androidApp:assembleRelease
./gradlew :androidApp:connectedDebugAndroidTest
```

The shared parser corpus covers completed and nearby-negative synthetic card,
bank, UPI, fee, refund, credit, transfer, and ATM messages; exact currency minor
units; explicit rejection/review reasons; conflicting facts; normalization; and
500 seeded malformed-input mutations. A real Room reparse regression verifies
that parser-v2 detected fields change without erasing user overrides. Seven
connected tests passed on `SpendTracker_API_37` (API 37).

The P1 review-fix pass added parser and Room regressions for excluding conflicting
facts from totals and for stopping amount capture before whitespace-separated
date/reference digits. Shared/JVM and Android unit tests, lint, iOS compilation,
debug/release assembly, and all seven connected API 37 tests passed.

## Known gaps

- Top-level destination survives configuration changes through the ViewModel but
  is not restored after process death.
- Parser templates remain a conservative controlled-MVP corpus rather than
  attempting universal bank coverage; unmatched messages are now explainable.
- A read-only transaction list exists, but filters, detail, and editing do not.
- Weekly and monthly aggregations do not exist.
- There is no `RECEIVE_SMS` live ingestion; foreground overlap reconciliation exists.
- The Settings shell exists, but delete-all and complete data-lifecycle controls
  do not.
- Real SMS-provider behavior and physical-device checks remain release work;
  automated importer integration uses a synthetic source and real Room database.
- Public release requires Google Play restricted-SMS-permission review material.

## Open questions

These do not block starting MVP-05. Resolve them in the owning slice and record
a decision:

1. Exact confidence threshold for automatic acceptance versus needs-review.
2. Whether merchant-wide category corrections are opt-in per edit or a separate
   explicit action.
3. Whether the source-SMS deep link is worth exposing in MVP after privacy and
   OEM-provider behavior are tested.
4. Final Google Play declaration/distribution path for SMS permission approval.

## Handoff update checklist

At the end of every slice:

- update the slice status and checked acceptance items in `MVP_PLAN.md`;
- replace this baseline/gaps section with actual behavior where relevant;
- record commands and device/API levels actually tested;
- add decisions or open questions discovered during implementation;
- remove the completed entry from `Active work`;
- identify the next safe, dependency-complete slice.
