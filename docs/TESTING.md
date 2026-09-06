# Testing strategy

## Principles

- Test business rules with fast, deterministic host tests first.
- Use fakes for SMS source, repository, clock, time zone, and scheduler.
- Exercise Android framework boundaries on an emulator or device.
- Every bug fix begins with a failing regression test when practical.
- Never use a real user's SMS database or copy real message text into fixtures.

## Test layers

### Shared unit tests

Run on the JVM without an emulator. Cover:

- amount and currency parsing, including separators and zero-fraction currencies;
- direction and transaction-kind classification;
- rejection of OTP, failed, declined, cancelled, and ambiguous messages;
- category rule priority and merchant normalization;
- default inclusion policy and user override precedence;
- source identity/deduplication behavior;
- three-month cutoff boundary with an injected clock;
- weekly/monthly ranges across month/year boundaries and time zones;
- aggregation by category and exclusion of non-INR amounts;
- reparse behavior preserving user overrides.

MVP-03 additionally fixes the import contract with host tests for exact cutoff
selection, safe progress, cancellation, partial-failure retry, reconciliation
overlap, version-1 migration, and a lazily generated 10,000-message stream whose
write batches never exceed 100 records. Regression tests also cover startup with
an abandoned `RUNNING` state and a null SMS-provider cursor.

Parser tests should be table-driven and contain only synthetic bank/message
templates. Add cases for whitespace, capitalization, Indian digit grouping,
missing merchant, overflow, malformed decimals, and repeated currency symbols.

MVP-04 fixes this contract with explicit accepted/review/rejected outcomes, a
representative India-first corpus, nearby negative cases, exact INR/foreign minor
units, conflicting-field review cases, and 500 deterministic malformed-input
mutations. Import tests assert privacy-safe aggregate rejection counts, and a
Room repository test reparses detected fields while preserving user overrides.

MVP-05 adds a table covering all thirteen fixed buckets, merchant normalization,
fee precedence and keyword collisions, and `Other` review semantics. Room tests
cover merchant-rule persistence, future/reparse application, deletion, explicit
transaction-override precedence, and effective category reporting queries.
They also verify durable review-reason resolution, normalized matching in both
repository implementations, and the version-1-to-version-3 migration chain.

MVP-07 adds shared period-calculator tests for Monday-start weeks, calendar
months, year and leap edges, Kolkata/UTC offsets, and a DST transition, plus
aggregator tests for included-INR-only headlines, exact category-sum invariants,
daily bucketing, counts, and comparison rounding. Android ViewModel tests use a
fixed clock and zone for period totals, switching, deep-link filters, and
zone-change recomputation; connected Compose tests cover the period selector,
headline, counts, category links, empty state, and daily-bar counts.

The source-message view adds ViewModel tests for found/dismiss, permission-gated
lookup, and clearing on close or selection change, plus Compose tests for the
found body, unavailable explanation, and the view-source intent. Source-view
fixtures are synthetic and the body is never logged.

Review resolution (D-018) adds parser tests for the `on X` merchant anchor and
its digit/possessive guards, filter tests proving MISSING_MERCHANT-only rows
leave review, and repository/ViewModel tests that a category save drops only
`UNKNOWN_CATEGORY` while amount/direction/type concerns survive with their
confidence. The single-value model (D-019) adds repository tests that re-imports
refresh untouched rows and skip user-edited rows, plus the version-1-to-5
migration chain.

### Android local unit tests

MVP-06 tests combined filter boundaries/currencies against Room, override
close/reopen persistence, editor-to-dashboard updates, reset, failed-save retry,
and observation recovery. Connected tests exercise combined filters/clear and
category/inclusion edits/reset through the Compose controls.

Cover ViewModel state transitions and Android-independent orchestration with
fakes: permission state, first import, retry, empty result, partial failure,
editing, deletion, and live refresh.

### Android instrumentation and Compose tests

Cover:

- Room DAO queries, uniqueness, transactions, and migrations;
- permission disclosure and denied/granted UI;
- navigation among dashboard, transactions, detail, and settings;
- list filters and category/inclusion edits;
- empty/loading/error/review states;
- dashboard totals rendered from a seeded test database;
- receiver-to-worker/repository integration where the platform permits;
- accessibility semantics for controls and financial values.

Use a test-only fake SMS adapter for reliable UI automation. The current
connected importer test streams a synthetic source through the production parser
and a real temporary Room database. Keep a smaller manual test for the real
Android provider boundary.

### Manual emulator/device tests

At each applicable slice, verify:

1. Fresh install and first denial.
2. Grant on retry and successful initial scan.
3. Empty inbox and inbox with no recognized transactions.
4. Repeat scan produces no duplicates.
5. Revocation in system settings produces a recoverable UI.
6. App restart/process death retains stored state and overrides.
7. A new synthetic debit SMS appears once and updates totals.
8. A foreign transaction is visible but does not alter INR totals.
9. Delete-all returns to a clean onboarding state.
10. Font scaling and TalkBack do not make primary actions unreachable.

Test at minimum API 26 and the current target API. Use at least one physical
Android phone before an MVP release because emulator telephony behavior is not a
complete substitute for OEM messaging providers.

## Commands

Fast shared suite:

```shell
./gradlew :shared:jvmTest
```

Local unit, lint, and debug build gate:

```shell
./gradlew :shared:jvmTest :androidApp:testDebugUnitTest
./gradlew :androidApp:lintDebug :androidApp:assembleDebug
```

Connected tests with a running emulator/device:

```shell
./gradlew :androidApp:connectedDebugAndroidTest
```

List devices and install the debug APK manually when diagnosing:

```shell
adb devices
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

On an Android emulator, a synthetic incoming SMS can be injected from the
extended controls or emulator console. Treat this as an integration check, not
as the only automated receiver test.

## Fixture policy

- Use fictitious senders, merchants, account hints, dates, and amounts.
- Ensure fixtures cannot be traced to a user or pasted production message.
- Keep positive and negative examples next to expected parsed/rejection results.
- No screenshots containing real inbox data belong in the repository.
- Sanitization means replacement, not merely masking one account number.

## Performance and reliability checks

Before release, create a generated synthetic corpus large enough to exceed a
typical three-month inbox and record on a named reference device/emulator:

- total scan time;
- database upsert time;
- peak memory behavior;
- UI responsiveness during import;
- repeat-import time and duplicate count.

The hard gates are no application-not-responding event, no raw-message list held
in memory, bounded batch/transaction sizes, and zero duplicates. Record measured
numbers in `STATUS.md` rather than declaring an unmeasured universal time limit.

## Per-slice exit gate

A feature slice is complete only when its tests in `MVP_PLAN.md` pass, existing
suites remain green, relevant manual cases above are checked, and the current
result is recorded in `STATUS.md`.
