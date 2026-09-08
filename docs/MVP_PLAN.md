# MVP implementation plan

This is the executable roadmap. Each slice delivers one observable capability,
has explicit dependencies, and can be implemented and tested before the next
slice begins.

## Status legend

- `Done`: acceptance and test gates passed; documentation reflects the result.
- `Ready`: dependencies are complete and the slice can be claimed.
- `Planned`: wait for listed dependencies unless the user reprioritizes.
- `In progress`: claimed in `STATUS.md` by one task/agent.
- `Blocked`: blocker and required decision are written in `STATUS.md`.

## Sequence

| ID | Feature slice | Status | Depends on | Primary user result |
| --- | --- | --- | --- | --- |
| MVP-00 | Feasibility baseline | Done | — | App can permission-gate and scan three months into an in-memory summary |
| MVP-01 | App foundation and testable UI shell | Done | MVP-00 | Navigable, previewable app states without using private SMS |
| MVP-02 | Local database and idempotent repository | Complete | MVP-01 | Parsed transactions survive restart and cannot duplicate |
| MVP-03 | Production import lifecycle | Complete | MVP-02 | Clear onboarding and durable three-month import/retry status |
| MVP-04 | Detection and parser hardening | Complete | MVP-02 | Supported messages become explainable, well-tested records |
| MVP-05 | Categorization and override rules | Complete | MVP-04 | Basic buckets are reliable and corrections persist |
| MVP-06 | Transaction list, filters, and detail/edit | Complete | MVP-03, MVP-05 | Users can inspect and correct the ledger |
| MVP-07 | Weekly and monthly dashboard | Complete | MVP-05, MVP-06 | Users see reproducible period and category totals |
| MVP-08 | New-message ingestion and reconciliation | Complete | MVP-03, MVP-04 | New financial SMS updates the ledger once |
| MVP-09 | Privacy/settings and data lifecycle | Complete | MVP-06, MVP-08 | Users control access, data, and currency explanations |
| MVP-10 | Hardening and controlled-release gate | Ready | MVP-01–MVP-09 | Accessible, performant, policy-ready MVP build |
| ENH-01 | Historical day/week/month dashboard ranges | Complete | MVP-07 | Users can browse earlier calendar days, weeks, and months |

MVP-03 and MVP-04 may proceed in parallel only if separate owners avoid the same
import contracts and coordinate schema/parser changes. By default, take slices
in table order.

---

## MVP-00 — Feasibility baseline

**Status:** Done

### Delivered

- KMP shared module with Android/JVM/iOS targets.
- Android Compose feasibility screen.
- Explicit `READ_SMS` request.
- Streaming three-month content-provider scan off the main thread.
- Initial deterministic parser and category enum.
- In-memory INR spend summary and unconverted foreign count.
- Shared parser/import-policy tests, lint, and debug build.

### Limitations carried forward

No persistence, navigation, durable deduplication, transaction views, aggregates,
live messages, or production permission/error lifecycle.

---

## MVP-01 — Application foundation and testable UI shell

**Status:** Done  
**Depends on:** MVP-00

### Goal

Create maintainable boundaries and a navigable product skeleton before adding
database and SMS complexity.

### Deliverables

- Split `MainActivity` into app/navigation, onboarding, dashboard, transactions,
  and settings packages.
- Introduce immutable UI-state models and constructor-injected ViewModels.
- Add a minimal Material 3 theme and move user-facing text to resources.
- Add bottom/top-level navigation for Dashboard, Transactions, and Settings.
- Add debug-only synthetic repository/data fixtures and Compose previews.
- Define shared repository and source contracts needed by later slices without
  implementing speculative backend interfaces.

### Acceptance criteria

- [x] App starts on onboarding when access/import has not completed.
- [x] Synthetic debug mode can show populated dashboard and transaction shell
      without SMS permission or real data.
- [x] All top-level destinations are reachable and retain selected state while
      the ViewModel is alive, including configuration changes.
- [x] Composables do not resolve SMS provider/database services directly.
- [x] Empty, loading, content, and error previews exist for primary screens.
- [x] Existing feasibility scan behavior remains reachable until MVP-03 replaces
      it, or is explicitly adapted behind the new orchestration boundary.

### Test gate

- [x] ViewModel navigation/state unit tests.
- [x] Compose tests for top-level navigation and principal empty/error states.
- [x] Shared tests, Android unit tests, lint, debug/release assemble, connected
      Compose tests, and emulator launch pass.

### Not in this slice

Room, durable imports, production dashboard calculations, or a live receiver.

---

## MVP-02 — Local database and idempotent repository

**Status:** Complete  
**Depends on:** MVP-01

### Goal

Make parsed transactions a safe local source of truth that survives restarts and
handles history/live overlap without duplicates.

### Deliverables

- Validate Room KMP compatibility; record outcome in `DECISIONS.md`.
- Implement the transaction, import-state, settings, and merchant-rule schema
  described in `DATA_AND_PRIVACY.md`.
- Add repository queries for chronological records, record detail, period totals,
  category totals, review items, and counts.
- Add transactional idempotent upsert keyed by provider ID and fingerprint.
- Preserve user category/inclusion overrides when detected fields are reparsed.
- Implement installation-local keyed fingerprint generation without persisting
  raw body.
- Add delete-all transaction covering database and fingerprint key lifecycle.

### Acceptance criteria

- [x] Stored transactions return after process death/app restart.
- [x] Importing the same synthetic source twice yields one transaction.
- [x] Provider-ID and fingerprint fallback collisions are tested explicitly.
- [x] Concurrent/reordered upserts cannot create duplicates.
- [x] Reparsing changes detected fields but not user overrides.
- [x] No database table or diagnostic contains a raw SMS body.
- [x] Database schema export/migration approach is configured.

### Test gate

- [x] DAO/repository host tests for insert, update, query, delete, persistence,
      concurrency, and uniqueness.
- [x] Version-1 schema fixture is exported. A migration test becomes applicable
      with the first version-2 migration.
- [x] Connected Android test for keyed-fingerprint stability and input separation.
- [x] Database close/reopen test with seeded synthetic data.

### Not in this slice

Final onboarding, parser expansion, or user-facing edit UI.

---

## MVP-03 — Production import lifecycle

**Status:** Complete
**Depends on:** MVP-02

### Goal

Turn the current scan button into a transparent, resumable three-month import
that writes through the repository and explains every state.

### Deliverables

- Prominent pre-permission disclosure matching actual use and retention.
- Permission state handling: not requested, denied, denied permanently, granted,
  and revoked later.
- Initial import use case with injected clock and three-calendar-month cutoff.
- Stream-to-parse-to-upsert pipeline with progress counters, cancellation, retry,
  and durable completion/failure state.
- Foreground reconciliation entry point based on the last successful scan.
- Summary of scanned, recognized, rejected/ignored, review, and saved counts.

### Acceptance criteria

- [x] System permission prompt only follows the app's disclosure and user action.
- [x] Denial does not crash or trap the user; retry/settings path is clear.
- [x] Only messages at or after the exact three-month cutoff are read.
- [x] A completed import populates the repository and app restart skips redundant
      first-import onboarding.
- [x] Interrupted/failed import can retry without duplicates.
- [x] Revocation stops reads and surfaces a recoverable status.
- [x] Body/sender/amount do not appear in logs or error text.

### Test gate

- [x] Unit tests for cutoff, progress, cancellation, retry, and error mapping.
- [x] Instrumentation test with a fake SMS source and real test database.
- [x] Manual fresh-install deny/grant/revoke/retry checks on emulator.
- [x] Generated high-volume corpus shows streaming/bounded memory behavior.

### Not in this slice

New-message broadcasts or broad parser-template coverage.

---

## MVP-04 — Detection and parser hardening

**Status:** Complete
**Depends on:** MVP-02

### Goal

Make transaction detection explainable and robust enough for a controlled MVP,
without cloud ML or retaining source text.

### Deliverables

- Split normalization, financial-message detection, amount parsing, direction,
  kind, merchant/account extraction, and confidence/rejection reason.
- Version parser output and define safe reparse behavior.
- Add representative synthetic patterns for major India-first card, bank debit,
  UPI, fee, refund/reversal, credit, transfer, and ATM wording.
- Handle INR symbols/aliases, Indian/international separators, supported foreign
  currencies, malformed values, and overflow.
- Introduce explicit `Rejected`/`NeedsReview`/`Accepted` outcomes or equivalent.
- Add safe aggregate diagnostics for unmatched/rejected reason counts.

### Acceptance criteria

- [x] OTP, balance-only, marketing, failure, and authorization-only fixtures are
      not accepted as completed spend.
- [x] Debit purchase and fee examples are included; transfer, withdrawal, credit,
      and refund examples are excluded by default.
- [x] Ambiguous/conflicting messages are reviewable rather than silently counted.
- [x] Amounts use exact minor units for all supported currencies.
- [x] Parser behavior is independent of Android and deterministic.
- [x] Every new template/rule has positive and nearby negative tests.

### Test gate

- [x] Table-driven shared parser corpus passes.
- [x] Property/fuzz-style tests cover whitespace, casing, punctuation, malformed
      amounts, and overflow without crashes.
- [x] Reparse tests preserve explicit user overrides through the repository.
- [x] No test fixture contains real customer data.

### Not in this slice

Remote models, inbox export, or automatic FX conversion.

---

## MVP-05 — Categorization and override rules

**Status:** Complete
**Depends on:** MVP-04

### Goal

Assign every accepted transaction to a basic bucket and make deterministic,
durable correction behavior available to later UI.

### Deliverables

- Isolate versioned category rules from parser mechanics.
- Normalize merchants without exposing raw sender/body.
- Implement the thirteen buckets in `PRODUCT.md` with defined precedence.
- Add effective category/inclusion resolution from detected and user fields.
- Add optional, explicit merchant-category rule creation and deletion.
- Mark `Other` and low-confidence assignments for review without excluding them
  solely because the category is uncertain.

### Acceptance criteria

- [x] Every accepted transaction has exactly one detected category.
- [x] Fee kind takes precedence over merchant keyword categorization.
- [x] Explicit transaction override wins over merchant and built-in rules.
- [x] User-approved merchant rule applies to future/reparsed matching merchants.
- [x] Deleting a merchant rule does not erase per-transaction overrides.
- [x] Category changes produce correct repository query results.

### Test gate

- [x] Shared table-driven category and precedence tests.
- [x] Repository tests for override persistence and merchant-rule lifecycle.
- [x] Regression tests for keyword collisions and `Other` fallback.

### Not in this slice

Custom category creation, category budgets, or remote classification.

---

## MVP-06 — Transaction list, filters, and detail/edit

**Status:** Complete
**Depends on:** MVP-03, MVP-05

### Goal

Let the user audit the local ledger and correct classifications before trusting
dashboard totals.

### Deliverables

- Paginated/lazy chronological transaction list.
- Filters for date range, category, included/excluded, review state, and currency.
- Rows showing merchant/fallback label, amount/currency, date, category, and
  excluded/review affordance.
- Detail view for parsed fields, inclusion reason, confidence/review status,
  category edit, include/exclude override, and override reset.
- Optional explicit source lookup by provider ID if privacy/OEM testing approves.
- Empty, no-filter-results, source-unavailable, and error states.

### Acceptance criteria

- [x] List ordering is deterministic for equal timestamps.
- [x] Filters compose correctly and can be cleared.
- [x] Editing category or inclusion updates the list immediately and survives
      app restart/reparse.
- [x] Foreign amount formatting uses its own currency/fraction digits.
- [x] Excluded records visibly explain why they do not affect spend.
- [x] No UI exposes full source body except an explicit ephemeral source view.

### Test gate

- [x] ViewModel tests for query/filter/edit/error state transitions.
- [x] Compose tests for list, combined filters, detail edits, and empty states.
- [x] DAO/query tests with boundary timestamps and currencies; UI uses lazy rows,
      with the parsed ledger observed in memory rather than database paging.
- [x] Manual 150% font scaling and basic TalkBack focus/navigation checks on API 37.

Optional source lookup is now implemented as an explicit on-demand dialog
(D-017); it remains subject to privacy/OEM behavior checks before release.
Spoken-output quality and API 26/physical-device accessibility remain release checks.

### Not in this slice

Transaction splitting, notes, receipt attachments, or manual non-SMS entry.

---

## MVP-07 — Weekly and monthly dashboard

**Status:** Complete
**Depends on:** MVP-05, MVP-06

### Goal

Provide the requested weekly and monthly spend views using auditable repository
data and exact calendar boundaries.

### Deliverables

- Shared aggregate use cases for current week/month, previous comparable period,
  daily series, and category breakdown.
- Dashboard period selector for Week and Month.
- Headline included INR total and previous-period absolute/percentage comparison.
- Category amounts and shares; simple accessible chart/list presentation.
- Foreign/excluded/review counts linked to filtered transaction views.
- Empty and no-INR-spend states that do not misleadingly show a successful zero.

### Acceptance criteria

- [x] Week starts Monday 00:00 and month uses local calendar boundaries.
- [x] Current partial-period comparison uses an explicitly tested rule documented
      in the UI copy/aggregation tests.
- [x] Only effectively included INR records affect headline/category totals.
- [x] Category totals sum exactly to the headline total.
- [x] Refund/credit/transfer/ATM/foreign defaults and user overrides behave as
      specified.
- [x] Tapping a category or excluded/review count opens matching transactions.
- [x] Time-zone changes recompute ranges deterministically without data loss.

### Test gate

- [x] Shared aggregation tests for day/week/month/year/leap/DST/time-zone edges.
- [x] Exact minor-unit and category-sum invariant tests.
- [x] ViewModel and Compose tests with a fixed clock and seeded repository.
- [x] Screenshot/manual visual checks for empty, small, large, and long-label data.

### Notes

- Aggregation is in-memory over the observed parsed ledger via shared
  `PeriodCalculator`/`ReportAggregator`; Room period queries remain available but
  are not used by the dashboard.
- Comparison rule and deep-link filters are recorded as D-016; the transaction
  filter gained a default-false `foreignOnly` dimension.
- Manual checks used the debug demo on `SpendTracker_API_37` at default and 150%
  font scale, plus category-to-transactions deep-link verification.

### Not in this slice

Budgets, forecasts, annual reports, net worth, or mixed-currency totals.

---

## MVP-08 — New-message ingestion and reconciliation

**Status:** Complete (reconciliation-primary)
**Depends on:** MVP-03, MVP-04

### Goal

Add each new supported financial SMS exactly once and recover from missed events.

### Deliverables (as implemented)

- Foreground reconciliation on every app open/resume, querying the inbox since
  the last successful scan with a safe overlap window (already present from the
  import lifecycle, D-011). UI observes the repository, so new records refresh
  list and dashboard automatically without a manual rescan.
- The initial three-month import also starts automatically once SMS access is
  granted during onboarding; no explicit scan button. A failed first scan
  exposes a retry action.
- The manual "Check for new messages" Settings button is removed; pickup is
  automatic on app open only.
- Non-sensitive status text only; no content-bearing notifications.
- A broadcast receiver is intentionally not added (see D-020): on Android 14+
  (target SDK 37) a non-default SMS app no longer receives full SMS bodies in
  broadcasts, so foreground reconciliation is the dependable universal path.
  Instant live pickup would require notification-listener access, kept as a
  separate future opt-in decision.

### Acceptance criteria

- [x] A supported new SMS appears once when the app is opened (reconciliation
      after the last scan; insert-only dedupe guarantees one record).
- [x] Reconciliation overlap and duplicate reads still yield one transaction.
- [x] Unsupported/failed messages do not change spend totals.
- [x] Permission revocation prevents access and produces recoverable UI state.
- [x] Process death/reboot scenarios recover through reconciliation on open.
- [x] User-edited rows survive reprocessing (`userEdited` freeze).

### Test gate

- [x] Coordinator/reconciliation overlap and dedupe unit tests.
- [x] Instrumentation integration test from a synthetic source to Room.
- [x] Reconcile-on-open ViewModel coverage.
- [ ] Real-provider physical-device receive/restart/reconcile checks before release.

### Not in this slice

MMS parsing, notification scraping, default-SMS-app behavior, or background network.

---

## MVP-09 — Privacy/settings and data lifecycle

**Status:** Complete  
**Depends on:** MVP-06, MVP-08

### Goal

Make the privacy contract inspectable and give the user control over permission,
stored data, import status, and currency limitations.

### Deliverables (as implemented)

- Settings screen with SMS access card (permission, three-month read window,
  automatic pickup note, manage-access action) and a Status card showing last
  scan time, parser version, and stored transaction count.
- Explanations: what counts as spend (included/excluded kinds), foreign-currency
  no-conversion behavior, and a local-only/offline privacy disclosure.
- Destructive `Delete all SpendTracker data` flow with confirmation; it clears
  the database and the fingerprint key via the application and returns to
  onboarding. Revoking SMS access is explained as a separate, non-destructive
  action.
- Manual reconciliation is no longer a control (reconciliation is automatic on
  open since MVP-08).
- Verified manifest/backup rules: `READ_SMS` only, backup disabled, no
  `INTERNET`, no network/analytics dependencies.

### Acceptance criteria

- [x] Delete-all removes transactions, rules, import state, settings, and the
      fingerprint key, then returns to onboarding.
- [x] Permission revocation and data deletion are distinct, accurately explained.
- [x] Last scan/status reflects the durable import state and updates only when a
      scan completes.
- [x] Foreign no-conversion policy is easy to find in Settings.
- [x] Fresh clear-data/reinstall leaves no app-retained financial record.
- [x] Release manifest contains only the necessary declared permission/feature.

### Test gate

- [x] Repository clear/database deletion is covered; fingerprint key reset is
      exercised by the Android Keystore test and the delete-all flow.
- [x] ViewModel tests for delete confirm/cancel/success/failure and Compose tests
      for the status card and delete dialog.
- [x] Manual clear-data, delete-all, revoke, and reinstall checks on the emulator.
- [x] Static manifest/dependency audit for network and telemetry additions.

### Not in this slice

Cloud export, account deletion, or sync privacy controls.

---

## ENH-01 — Historical day/week/month dashboard ranges

**Status:** Complete
**Depends on:** MVP-07

### Goal

Let users inspect dashboard totals and matching transactions for individual
calendar days and for earlier weeks and months, instead of limiting reporting to
the current week or month.

### Deliverables

- Add Day alongside Week and Month dashboard periods.
- Add previous/next calendar-period navigation, with future periods unavailable.
- Keep current partial-period comparisons like-for-like; compare a completed
  historical period with the complete immediately preceding period.
- Preserve dashboard-to-transaction deep links using the visibly selected range.

### Acceptance criteria

- [x] Day, week, and month boundaries use local calendar midnights.
- [x] Users can step backward through multiple periods and return toward today.
- [x] The next action is disabled while the current period is selected.
- [x] Period changes reset to the current period; changing granularity never
      carries an ambiguous offset into the new calendar unit.
- [x] Category, excluded, and foreign links reproduce the selected historical range.

### Test gate

- [x] Shared tests cover day boundaries, historical offsets, full preceding
      comparisons, month/year/leap edges, and time zones.
- [x] ViewModel tests cover period switching, repeated navigation, future guard,
      comparison totals, and deep-link ranges.
- [x] Compose tests cover Day selection and previous/next control state.
- [x] Shared/JVM, Android unit, lint, debug assembly, and the API 37 connected
      test suite pass.

---

## MVP-10 — Hardening and controlled-release gate

**Status:** Ready (deferred while product enhancements are in progress)
**Depends on:** MVP-01 through MVP-09

### Goal

Turn feature-complete code into a stable build suitable for controlled testers
and a Google Play permission review submission.

### Deliverables

- Close crashes, ANRs, lifecycle races, import corruption, and accessibility gaps.
- Run generated high-volume corpus and document reference-device measurements.
- Validate API 26 and target API plus at least one physical phone/OEM.
- Complete parser false-positive/negative review using synthetic templates.
- Verify database migrations, process death, upgrade install, and delete-all.
- Create release build configuration, signing handoff checklist, store disclosure,
  privacy policy, and restricted SMS Permissions Declaration material.
- Remove debug fixtures/logging from release behavior and audit dependencies.

### Release groundwork already completed

- Code gate green: full shared/JVM, Android unit, lint, debug/release assembly,
  iOS simulator compile, and connected Compose/instrumentation on API 37.
- Automated high-volume coverage: a generated 10,000-message import is bounded
  (write batches never exceed 100) with zero duplicates; wall-clock
  reference-device measurements remain a physical-device release step.
- Migration chain (v1→v5), delete-all, process-death (abandoned RUNNING), and
  reinstall behaviors verified by automated tests and emulator checks.
- Release signing is wired to an optional, git-ignored `keystore.properties`;
  without it the release APK builds unsigned. Release variant returns no demo
  repository; there are no `Log`/analytics/network calls to strip, and the merged
  release manifest declares only `READ_SMS` (backup disabled, no `INTERNET`).
- Release material added: `SIGNING_RELEASE.md`, `STORE_LISTING_AND_PRIVACY.md`,
  and `SMS_PERMISSIONS_DECLARATION.md`.
- Accessibility: font-scale (150%) and TalkBack/`contentDescription` spot checks
  passed on emulator primary paths in earlier slices.

### Acceptance criteria

- [ ] All functional requirements in `PRODUCT.md` are demonstrable on API 26,
      the target API, and a physical Android/OEM device after enhancement work.
- [ ] Full automated suite, lint, signed release build, and manual matrix pass.
- [ ] No known P0/P1 data-loss, duplication, privacy, crash, accessibility, or
      total-correctness defect remains after the final review pass.
- [x] High-volume import is bounded with zero duplicates (automated 10k corpus).
- [ ] TalkBack/font-scale/contrast checks pass on all primary paths in the final UI.
- [ ] Public distribution: pending physical-device/OEM and Google Play SMS
      permission declaration review (external, release-blocking).

### Test gate

- [ ] Every command and matrix item in `TESTING.md` runs green on the final code.
- [x] Clean install, upgrade, permission denial/revocation, process death, and
      delete-all pass (emulator + automated).
- [x] Independently recalculated seeded weekly/monthly totals match the dashboard.
- [x] Database, Logcat, screenshots, and release manifest inspected for privacy
      leaks: no raw SMS or sensitive identifiers found.
- [ ] Physical-device incoming-SMS end-to-end and OEM-provider behavior pass.

### Remaining before public release (external)

- Physical Android phone/OEM SMS-provider validation, including a real incoming
  financial SMS reconcile.
- Google Play restricted-SMS permission eligibility + declaration submission.

---

## Post-MVP candidates

These require new product decisions and are not silently part of the slices:

- manual transactions and statement import;
- budgets and bill/subscription reminders;
- authenticated encrypted backup/sync;
- historically dated FX conversion;
- user-created categories and split transactions;
- alternate data sources and a viable iOS ingestion model;
- privacy-preserving opt-in diagnostics.
