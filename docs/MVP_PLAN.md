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
| MVP-03 | Production import lifecycle | Planned | MVP-02 | Clear onboarding and durable three-month import/retry status |
| MVP-04 | Detection and parser hardening | Planned | MVP-02 | Supported messages become explainable, well-tested records |
| MVP-05 | Categorization and override rules | Planned | MVP-04 | Basic buckets are reliable and corrections persist |
| MVP-06 | Transaction list, filters, and detail/edit | Planned | MVP-03, MVP-05 | Users can inspect and correct the ledger |
| MVP-07 | Weekly and monthly dashboard | Planned | MVP-05, MVP-06 | Users see reproducible period and category totals |
| MVP-08 | New-message ingestion and reconciliation | Planned | MVP-03, MVP-04 | New financial SMS updates the ledger once |
| MVP-09 | Privacy/settings and data lifecycle | Planned | MVP-06, MVP-08 | Users control access, data, and currency explanations |
| MVP-10 | Hardening and controlled-release gate | Planned | MVP-01–MVP-09 | Accessible, performant, policy-ready MVP build |

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

**Status:** Planned  
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

- [ ] System permission prompt only follows the app's disclosure and user action.
- [ ] Denial does not crash or trap the user; retry/settings path is clear.
- [ ] Only messages at or after the exact three-month cutoff are read.
- [ ] A completed import populates the repository and app restart skips redundant
      first-import onboarding.
- [ ] Interrupted/failed import can retry without duplicates.
- [ ] Revocation stops reads and surfaces a recoverable status.
- [ ] Body/sender/amount do not appear in logs or error text.

### Test gate

- [ ] Unit tests for cutoff, progress, cancellation, retry, and error mapping.
- [ ] Instrumentation test with a fake SMS source and real test database.
- [ ] Manual fresh-install deny/grant/revoke/retry checks on emulator.
- [ ] Generated high-volume corpus shows streaming/bounded memory behavior.

### Not in this slice

New-message broadcasts or broad parser-template coverage.

---

## MVP-04 — Detection and parser hardening

**Status:** Planned  
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

- [ ] OTP, balance-only, marketing, failure, and authorization-only fixtures are
      not accepted as completed spend.
- [ ] Debit purchase and fee examples are included; transfer, withdrawal, credit,
      and refund examples are excluded by default.
- [ ] Ambiguous/conflicting messages are reviewable rather than silently counted.
- [ ] Amounts use exact minor units for all supported currencies.
- [ ] Parser behavior is independent of Android and deterministic.
- [ ] Every new template/rule has positive and nearby negative tests.

### Test gate

- [ ] Table-driven shared parser corpus passes.
- [ ] Property/fuzz-style tests cover whitespace, casing, punctuation, malformed
      amounts, and overflow without crashes.
- [ ] Reparse tests preserve explicit user overrides through the repository.
- [ ] No test fixture contains real customer data.

### Not in this slice

Remote models, inbox export, or automatic FX conversion.

---

## MVP-05 — Categorization and override rules

**Status:** Planned  
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

- [ ] Every accepted transaction has exactly one detected category.
- [ ] Fee kind takes precedence over merchant keyword categorization.
- [ ] Explicit transaction override wins over merchant and built-in rules.
- [ ] User-approved merchant rule applies to future/reparsed matching merchants.
- [ ] Deleting a merchant rule does not erase per-transaction overrides.
- [ ] Category changes produce correct repository query results.

### Test gate

- [ ] Shared table-driven category and precedence tests.
- [ ] Repository tests for override persistence and merchant-rule lifecycle.
- [ ] Regression tests for keyword collisions and `Other` fallback.

### Not in this slice

Custom category creation, category budgets, or remote classification.

---

## MVP-06 — Transaction list, filters, and detail/edit

**Status:** Planned  
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

- [ ] List ordering is deterministic for equal timestamps.
- [ ] Filters compose correctly and can be cleared.
- [ ] Editing category or inclusion updates the list immediately and survives
      app restart/reparse.
- [ ] Foreign amount formatting uses its own currency/fraction digits.
- [ ] Excluded records visibly explain why they do not affect spend.
- [ ] No UI exposes full source body except an explicit ephemeral source view.

### Test gate

- [ ] ViewModel tests for query/filter/edit/error state transitions.
- [ ] Compose tests for list, combined filters, detail edits, and empty states.
- [ ] DAO paging/query tests with boundary timestamps and currencies.
- [ ] Manual font scaling and TalkBack pass for list and editor.

### Not in this slice

Transaction splitting, notes, receipt attachments, or manual non-SMS entry.

---

## MVP-07 — Weekly and monthly dashboard

**Status:** Planned  
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

- [ ] Week starts Monday 00:00 and month uses local calendar boundaries.
- [ ] Current partial-period comparison uses an explicitly tested rule documented
      in the UI copy/aggregation tests.
- [ ] Only effectively included INR records affect headline/category totals.
- [ ] Category totals sum exactly to the headline total.
- [ ] Refund/credit/transfer/ATM/foreign defaults and user overrides behave as
      specified.
- [ ] Tapping a category or excluded/review count opens matching transactions.
- [ ] Time-zone changes recompute ranges deterministically without data loss.

### Test gate

- [ ] Shared aggregation tests for day/week/month/year/leap/DST/time-zone edges.
- [ ] Exact minor-unit and category-sum invariant tests.
- [ ] ViewModel and Compose tests with a fixed clock and seeded repository.
- [ ] Screenshot/manual visual checks for empty, small, large, and long-label data.

### Not in this slice

Budgets, forecasts, annual reports, net worth, or mixed-currency totals.

---

## MVP-08 — New-message ingestion and reconciliation

**Status:** Planned  
**Depends on:** MVP-03, MVP-04

### Goal

Add each new supported financial SMS promptly and exactly once, while recovering
from missed events.

### Deliverables

- Add the minimum required Android receive permission and receiver declaration.
- Receiver performs minimal extraction/enqueue work and never logs a body.
- Durable worker/import coordinator invokes the same parser/upsert pipeline.
- Foreground/startup reconciliation queries messages since last successful scan
  with a safe overlap window.
- UI observes repository changes and refreshes list/dashboard automatically.
- Non-sensitive success/failure status; no content-bearing notifications.

### Acceptance criteria

- [ ] A supported synthetic incoming SMS appears once without manual rescan.
- [ ] Duplicate broadcast, worker retry, and reconciliation overlap still yield
      one transaction.
- [ ] Unsupported/failed messages do not change spend totals.
- [ ] Permission revocation prevents access and produces recoverable UI state.
- [ ] Process death/reboot scenarios recover through reconciliation.
- [ ] Existing user overrides survive any later reprocessing.

### Test gate

- [ ] Unit tests for receiver/coordinator input, retry, and dedupe behavior.
- [ ] Instrumentation integration test from fake/live event to Room observation.
- [ ] Manual emulator injected-SMS test.
- [ ] Physical-device receive/revoke/restart/reconcile checks before release.

### Not in this slice

MMS parsing, notification scraping, default-SMS-app behavior, or background network.

---

## MVP-09 — Privacy/settings and data lifecycle

**Status:** Planned  
**Depends on:** MVP-06, MVP-08

### Goal

Make the privacy contract inspectable and give the user control over permission,
stored data, import status, and currency limitations.

### Deliverables

- Settings/status screen showing permission state, three-month policy, last scan,
  parser version, stored counts, and local-only explanation.
- Links/actions for system permission settings and manual reconciliation.
- Destructive `Delete all SpendTracker data` flow with confirmation.
- Clear explanation of included/excluded kinds and foreign-currency behavior.
- Privacy-policy draft and in-app prominent disclosure consistent with behavior.
- Verify backup/data-extraction rules and absence of internet/analytics permissions.

### Acceptance criteria

- [ ] Delete-all removes transactions, overrides/rules, import state, settings,
      and fingerprint key, then returns to onboarding.
- [ ] Permission revocation and data deletion are distinct, accurately explained.
- [ ] Last scan/status updates only after the corresponding operation succeeds.
- [ ] Foreign transactions and no-conversion policy are easy to find.
- [ ] Fresh reinstall/clear-data leaves no app-retained financial record.
- [ ] Release manifest contains only necessary declared permissions/features.

### Test gate

- [ ] Repository/database deletion and key-reset tests.
- [ ] ViewModel/Compose tests for confirmation, cancel, success, and failure.
- [ ] Manual clear-data, revoke, reinstall, and backup-rule checks.
- [ ] Static manifest/dependency audit for network and telemetry additions.

### Not in this slice

Cloud export, account deletion, or sync privacy controls.

---

## MVP-10 — Hardening and controlled-release gate

**Status:** Planned  
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

### Acceptance criteria

- [ ] All functional requirements in `PRODUCT.md` are demonstrable.
- [ ] Full automated suite, lint, release build, and manual matrix pass.
- [ ] No known P0/P1 data-loss, duplication, privacy, crash, or total-correctness
      defect remains.
- [ ] High-volume import remains responsive with bounded raw-message memory and
      zero duplicates; measurements are recorded in `STATUS.md`.
- [ ] TalkBack/font-scale/contrast checks pass on all primary paths.
- [ ] Public distribution does not begin until SMS permission eligibility and
      declaration are reviewed against current Google Play policy.

### Test gate

- [ ] Run every command and matrix item in `TESTING.md`.
- [ ] Clean install, upgrade, permission denial/revocation, process death, and
      physical incoming-SMS end-to-end tests pass.
- [ ] Independently recalculate seeded weekly/monthly totals and compare exactly.
- [ ] Inspect database, Logcat, screenshots, and release manifest for privacy leaks.

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
