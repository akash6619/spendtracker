# Current status

- Last updated: 2026-09-06
- Current milestone: MVP plan feature-complete (MVP-10 gate done; external
  physical-device and Google Play review remain before public distribution)
- Next recommended slice: none (all MVP slices complete). Public release requires
  the external steps in MVP-10's remaining items.
- Active work: none

## Active work

Agents must claim work here before implementation and clear the row at handoff.

| Slice | Agent/task | Files or area | Started | Notes |
| --- | --- | --- | --- | --- |

## Implemented now

- Two Gradle modules: `androidApp` and `shared`.
- KMP targets for Android, JVM host tests, iOS device, and iOS simulator.
- Single-activity Compose/Material 3 app shell with separate onboarding,
  dashboard, transactions, settings, theme, format, and common-component files.
- Bottom navigation among Dashboard, Transactions, and Settings.
- Immutable `AppUiState` driven by a constructor-injected `AppViewModel`.
- Shared `MessageSource` and durable `TransactionRepository` contracts.
- Room 3 KMP database with bundled SQLite and Android, JVM, and iOS builders.
- Version-3 schema for transactions, review reasons, import state, settings, and
  merchant rules, with exported JSON fixtures and a tested v1-to-v3 migration chain.
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
- Version-3 parser outcomes explicitly distinguish accepted, needs-review, and
  rejected messages with privacy-safe reason enums.
- India-first synthetic coverage includes card/bank debit, UPI purchase, fee,
  refund/reversal, credit, transfer, and ATM withdrawal wording.
- Exact amount parsing supports INR aliases, Indian and international grouping,
  supported foreign currencies, malformed precision, and overflow rejection.
- Attempt-local aggregate rejection-reason counts provide safe diagnostics; no
  source content or identifiers are included in the Room v3 schema.
- Conflicting amount/direction records remain visible but default to excluded
  from spend, and that detected inclusion survives Room round-trips.
- Amount matching stops before whitespace-separated dates or references instead
  of merging their digits into the transaction amount.
- Versioned `TransactionCategorizer` and `MerchantNormalizer` are isolated from
  parsing and cover all thirteen fixed MVP categories with deterministic fallback.
- Durable user-approved merchant rules apply during future/reparsed imports;
  explicit transaction overrides remain higher priority and survive rule deletion.
- Merchant rules cannot override fee categories, clear only category-specific
  review uncertainty, and are loaded once per import batch rather than per row.
- Both Room and debug repositories normalize incoming merchants before rule lookup;
  durable review-reason enums preserve unrelated ambiguity across restarts.
- `Other` categorization produces a review outcome without excluding an otherwise
  valid purchase solely because its category is uncertain.
- Initial kind, direction, merchant, account hint, category, and confidence rules.
- Credits/refunds/transfers/ATM withdrawals excluded from spend by policy.
- Persisted results feeding a dashboard summary and chronological transaction list.
- Shared `PeriodCalculator` produces Monday-start week and local-calendar-month
  windows, plus same-elapsed-day previous-period comparison windows, from an
  injected instant and time zone; DST transitions only change elapsed hours.
- Shared `ReportAggregator` derives included-INR headline, exact category sums,
  daily series, and foreign/excluded/review counts from the observed ledger.
- Dashboard period selector (Week/Month), headline with previous-period
  percentage, category breakdown with shares, accessible daily-spend bars, and
  explicit no-INR and empty states.
- Category, excluded, foreign, and review facts deep link to Transactions with
  the same period range and matching filter; `TransactionFilter` gained a
  default-false `foreignOnly` dimension.
- Dashboard facts recompute on ledger changes, period switches, and app resume,
  so time-zone changes regroup boundaries without data loss (D-016).
- On-demand `View source message` in transaction detail resolves the persisted
  SMS provider row ID at display time and verifies the fetched row against the
  stored installation-local fingerprint before showing anything, so a reused
  provider ID can never display an unrelated SMS. The body stays in an ephemeral
  dialog, is cleared on dismiss/selection change/close, and is never persisted
  or logged (D-017). Unavailable messages and revoked permission get explicit
  safe explanations.
- Saving a category resolves only the category review concern (D-018): an
  explicit category drops `UNKNOWN_CATEGORY`, other stored reasons survive, and
  confidence follows the surviving set. A missing merchant no longer flags
  review and the parser adds an `on X` merchant anchor. Review is hidden from
  the UI (list, detail, dashboard, filter) until a full-transaction edit can
  close all concerned fields; the data-layer reasons and rule remain intact.
  The reset-to-defaults action is gone; detail editors start from effective
  (detected) values and every save stores the chosen values.
- Single-value model (D-019): Room schema version 4 drops the detected/user
  override split, category keeps one value, inclusion is a non-null boolean
  shown as a detail toggle at top right; version 5 adds `userEdited`. Imports
  stay idempotent (UNIQUE provider-ID and fingerprint indexes) and refresh only
  untouched rows, so an improved parser can re-derive fields while user-edited
  rows survive re-import, duplicate broadcasts, and reconciliation overlap.
  The v3-to-v4 fold also migrates pre-model edits: effective category and
  inclusion become the single values and `userEdited` is set, so migrated
  corrections keep their re-import protection. Migration chain tested from
  version 1.
- Automatic ingestion (D-020/MVP-08): the three-month initial scan starts on its
  own once SMS access is granted (no button; retry shown only after a failed
  first scan), and new financial messages are reconciled automatically on every
  app open. Verified end-to-end on the API 37 emulator with injected SMS (₹850 +
  ₹240 both appeared after reopen, stored once).
- Privacy/settings and data lifecycle (D-021/MVP-09): Settings shows SMS access
  and the three-month window, a status card (last scan, parser version, stored
  count), what-counts-as-spend and foreign no-conversion explanations, and an
  in-app privacy disclosure. `Delete all SpendTracker data` clears the database
  and fingerprint key after confirmation and returns to onboarding; revoking
  access is explained as the separate non-destructive action. Manifest audit:
  `READ_SMS` only, backup disabled, no `INTERNET`, no network/analytics deps.
- Release gate (MVP-10): optional release signing via git-ignored
  `keystore.properties`; release variant has no demo repository; no `Log` or
  analytics calls exist. Release material added (`SIGNING_RELEASE.md`,
  `STORE_LISTING_AND_PRIVACY.md`, `SMS_PERMISSIONS_DECLARATION.md`). Automated
  10k-message import is bounded with zero duplicates. Merged release manifest:
  `READ_SMS` only, `allowBackup=false`, no `INTERNET`.
- Sixty shared JVM tests, twenty-four Android local tests, fourteen connected
  Compose tests, two connected importer/provider tests, and one connected
  Keystore test.
- No raw SMS persistence, login, backend, or internet permission.
- Thirty-three shared JVM tests, twelve Android local tests, six connected Compose
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
that versioned parser fields change without erasing user overrides. Seven
connected tests passed on `SpendTracker_API_37` (API 37).

The P1 review-fix pass added parser and Room regressions for excluding conflicting
facts from totals and for stopping amount capture before whitespace-separated
date/reference digits. Shared/JVM and Android unit tests, lint, iOS compilation,
debug/release assembly, and all seven connected API 37 tests passed.

On 2026-09-05, the MVP-05 gate completed successfully:

```shell
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:jvmTest \
  :androidApp:testDebugUnitTest :androidApp:lintDebug \
  :androidApp:assembleDebug :androidApp:assembleRelease
ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:connectedDebugAndroidTest
```

Shared tests cover all category buckets, fee/merchant collisions, normalization,
`Other` review behavior, effective override precedence, persistent merchant-rule
creation/deletion, reparse application, and category-total queries. Seven connected
tests passed on the API 37 emulator; the paired OnePlus was explicitly excluded
from instrumentation so its locally imported data remained untouched.

On 2026-09-06, the MVP-05 review-hardening gate completed successfully with the
same two commands. It added fee-precedence, resolved-review-state, normalized
in-memory matching, parser/category version linkage, batch rule lookup, and
version-1-to-version-3 migration regressions. Host/iOS/lint/APK checks and all
seven emulator-only connected tests passed; no physical device was targeted.

On 2026-09-06, MVP-06 passed shared/JVM and Android unit tests, iOS simulator
compilation, lint, debug/release builds, and nine connected tests on emulator-5554
(API 37). New checks cover combined filters and exact date boundaries, effective
overrides, Room close/reopen persistence, save/reset, failed-save retry, load
recovery, and Compose editor/filter flows. Manual synthetic-demo inspection at
150% font scale confirmed wrapping, scrolling, Save/Reset reachability, and
TalkBack focus/menu/back navigation. Emulator font/accessibility settings were
restored; the OnePlus was not targeted. Spoken-output quality and API 26 remain
release validation items.

MVP-06 now provides lazy chronological rows; date/category/inclusion/review/currency
filters; parsed detail with exclusion and review explanations; category/inclusion
override saves and reset. Saved edits update the list and dashboard and keep the
selected detail open even when it no longer matches filters. Source viewing is
explicitly unavailable, and no raw SMS is read by these screens.

On 2026-09-06, the MVP-07 gate completed successfully:

```shell
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:jvmTest \
  :androidApp:testDebugUnitTest :androidApp:lintDebug \
  :androidApp:assembleDebug :androidApp:assembleRelease
ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:connectedDebugAndroidTest
```

The shared suite includes week/month boundaries across month, year, leap,
DST, and Kolkata/UTC zones, exact category-sum invariants, comparison rounding,
daily bucketing, and foreign/excluded/review counts. Eighteen Android unit tests
cover fixed-clock dashboard totals, period switching, comparison percentages,
deep-link filters, and zone-change recomputation on resume. Fourteen connected Compose tests and the
importer/Keystore tests passed on emulator-5554 (API 37),
including the new DashboardScreenTest coverage.

Manual checks used the debug demo on `SpendTracker_API_37`: week and month
windows, headline, comparison, excluded/foreign/review counts, category
breakdown with shares, daily bars with per-day descriptions, a category
deep link landing on a filtered Transactions list, and a 150% font-scale pass.
The emulator font scale was restored afterwards; no physical device was targeted.

After the MVP-07 gate, the source-message view (MVP-06 optional item) completed:
a detail-screen `View source message` action resolves the provider row ID on
demand and shows sender, timestamp, and body in an ephemeral dialog. The same
full build gate passed with twenty-one Android unit tests, seventeen connected
tests (fourteen Compose), and lint/debug/release assembly. Manual checks on the
emulator confirmed the dialog and its permission-revoked explanation; the
live-found path with a real provider row remains release validation on a
physical device.

On 2026-09-06, the MVP-10 gate completed its code and material deliverables:

```shell
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:jvmTest \
  :androidApp:testDebugUnitTest :androidApp:lintDebug \
  :androidApp:assembleDebug :androidApp:assembleRelease
ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:connectedDebugAndroidTest
```

Result: sixty shared tests, twenty-eight Android unit tests, and twenty-three
connected tests (Compose, importer/provider, Keystore, source lookup) all pass
on the API 37 emulator. The 10,000-message import coverage remains bounded
(write batches ≤ 100) with zero duplicates. Delete-all and process-death paths
were verified on the emulator, and a 150% font-scale pass succeeded on primary
paths. The merged release manifest declares `READ_SMS` only, disables backup,
and contains no `INTERNET`. No raw SMS or sensitive identifiers appeared in
Logcat or fixtures. Remaining before public distribution: physical
phone/OEM-provider validation (including a real incoming financial SMS) and the
Google Play restricted-SMS permission declaration review; these are external
and release-blocking.

## Known gaps

- Top-level destination survives configuration changes through the ViewModel but
  is not restored after process death.
- Parser templates remain a conservative controlled-MVP corpus rather than
  attempting universal bank coverage; unmatched messages are now explainable.
- Database paging is deferred; lazy UI rows filter the observed parsed ledger.
  Unsaved editor drafts are not restored after process death.
- Source viewing relies on the system SMS provider; live-found-path behavior on
  OEM providers and physical devices remains release work.
- There is no `RECEIVE_SMS` live ingestion (D-020): on Android 14+ a non-default
  SMS app cannot receive full SMS bodies in broadcasts. New messages are picked
  up by automatic foreground reconciliation on every app open; the manual
  "Check for new messages" button was removed. Instant live pickup via
  notification-listener access remains a post-MVP opt-in candidate.
- Real SMS-provider behavior and physical-device checks remain release work;
  automated importer integration uses a synthetic source and real Room database.
- Public release requires Google Play restricted-SMS-permission review material.

## Open questions

These do not block starting MVP-08. Resolve them in the owning slice and record
a decision:

1. Exact confidence threshold for automatic acceptance versus needs-review.
2. Final Google Play declaration/distribution path for SMS permission approval.
3. Widening merchant detection beyond `at`/`to`/`on`/`info:` anchors and its
   effect on category quality.

## Handoff update checklist

At the end of every slice:

- update the slice status and checked acceptance items in `MVP_PLAN.md`;
- replace this baseline/gaps section with actual behavior where relevant;
- record commands and device/API levels actually tested;
- add decisions or open questions discovered during implementation;
- remove the completed entry from `Active work`;
- identify the next safe, dependency-complete slice.
