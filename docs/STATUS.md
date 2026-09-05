# Current status

- Last updated: 2026-09-05
- Current milestone: MVP-02 local database and idempotent repository complete
- Next recommended slice: MVP-03 production import lifecycle
- Active work: None

## Active work

Agents must claim work here before implementation and clear the row at handoff.

| Slice | Agent/task | Files or area | Started | Notes |
| --- | --- | --- | --- | --- |
| None | — | — | — | Documentation convention update complete |

## Implemented now

- Two Gradle modules: `androidApp` and `shared`.
- KMP targets for Android, JVM host tests, iOS device, and iOS simulator.
- Single-activity Compose/Material 3 app shell with separate onboarding,
  dashboard, transactions, settings, theme, format, and common-component files.
- Bottom navigation among Dashboard, Transactions, and Settings.
- Immutable `AppUiState` driven by a constructor-injected `AppViewModel`.
- Shared `MessageSource` and durable `TransactionRepository` contracts.
- Room 3 KMP database with bundled SQLite and Android, JVM, and iOS builders.
- Version-1 schema for transactions, import state, settings, and merchant rules,
  with an exported JSON schema fixture.
- Chronological, detail, period, category-total, review, and count DAO queries.
- Transactional provider-ID/fingerprint upsert with provider-reuse handling,
  concurrent deduplication, and user-override preservation.
- Android SMS fingerprints use an installation-local HMAC-SHA256 key held in
  Android Keystore; raw bodies are discarded after parsing and fingerprinting.
- Application-scoped database/repository construction and a full local-reset
  operation that clears stored facts before deleting the fingerprint key.
- Android `SmsMessageScanner` plus a debug/test-only in-memory repository.
- Debug-only synthetic repository with five safe sample records; release builds
  cannot construct it.
- Empty, loading, content, and error previews for primary screens.
- Android strings/plurals resources and a minimal light/dark Material theme.
- Runtime `READ_SMS` permission request with short privacy explanation.
- `SmsInboxReader` streaming inbox rows from the previous three months on IO.
- Portable transaction/money/category models.
- Deterministic parsing for initial INR, USD, EUR, GBP, and JPY patterns.
- Initial kind, direction, merchant, account hint, category, and confidence rules.
- Credits/refunds/transfers/ATM withdrawals excluded from spend by policy.
- Persisted results feeding a dashboard summary and chronological transaction list.
- No raw SMS persistence, login, backend, or internet permission.
- Twelve shared JVM tests, three ViewModel unit tests, two connected Compose
  tests, and one connected Android Keystore fingerprint test.
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

## Known gaps

- The app still opens onboarding after process death even when transactions are
  stored; durable import/onboarding state is MVP-03.
- Top-level destination survives configuration changes through the ViewModel but
  is not restored after process death; durable state begins in MVP-02/MVP-03.
- Parser template coverage is intentionally small and has no rejection reason.
- A read-only transaction list exists, but filters, detail, and editing do not.
- Weekly and monthly aggregations do not exist.
- There is no `RECEIVE_SMS` live ingestion or reconciliation state.
- The Settings shell exists, but delete-all and complete data-lifecycle controls
  do not.
- Compose coverage currently exercises only the app shell; permission/provider
  integration tests and physical-device checks are not yet present.
- Public release requires Google Play restricted-SMS-permission review material.

## Open questions

These do not block starting MVP-02. Resolve them in the owning slice and record
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
