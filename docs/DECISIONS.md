# Decision log

Accepted decisions are stable context. Change one only through an explicit new
entry that records the reason, migration impact, and affected tests.

## D-001 — Android-first with a Kotlin Multiplatform core

- Date: 2026-09-04
- Status: Accepted
- Decision: Build the MVP with native Android Jetpack Compose and Android SMS
  APIs. Keep domain logic in KMP common code and use native UI per platform.
- Reason: Android provides the required SMS access; native integration is direct,
  while KMP keeps parser and reporting logic reusable.
- Consequence: UI is not shared. iOS needs a separate SwiftUI shell and cannot
  assume general SMS inbox access.

## D-002 — Three-calendar-month initial import

- Date: 2026-09-04
- Status: Accepted
- Decision: Initial history scan begins at `now minus three calendar months`.
- Reason: This gives useful recent history without scanning an unlimited inbox.
- Consequence: The cutoff needs an injected clock and boundary tests. Parsed
  records remain after they later age beyond three months.

## D-003 — Do not persist raw SMS bodies

- Date: 2026-09-04
- Status: Accepted
- Decision: Process one body ephemerally; store parsed fields, provider row ID,
  and a keyed source fingerprint.
- Reason: Reduces exposure of private and unrelated content. Storage capacity is
  not the primary concern.
- Consequence: Source viewing depends on the SMS still existing and permission
  remaining granted. Missing source text does not invalidate parsed data.

## D-004 — Offline-only, account-free MVP

- Date: 2026-09-04
- Status: Accepted
- Decision: No login, backend, analytics/ads SDK, or `INTERNET` permission.
- Reason: Meets the initial privacy and on-device requirement and limits scope.
- Consequence: No sync, recovery, remote configuration, or online FX rates.

## D-005 — Preserve original currency; no automatic FX in MVP

- Date: 2026-09-04
- Status: Accepted
- Decision: INR is the headline reporting currency. Foreign transactions retain
  original currency/minor units and are excluded from INR aggregates.
- Reason: Correct historical conversion needs a trustworthy dated rate, which
  conflicts with an offline-only first release.
- Consequence: UI must disclose excluded foreign records and never add currencies.

## D-006 — Default definition of spend

- Date: 2026-09-04
- Status: Accepted
- Decision: Debit purchases and fees count as spend. Credits, refunds, transfers,
  and ATM cash withdrawals do not. Explicit user inclusion overrides take
  precedence.
- Reason: Transfers are not consumption, and cash withdrawal lacks information
  about when the cash is actually spent.
- Consequence: Excluded records remain visible and explainable.

## D-007 — Deterministic local rules before ML

- Date: 2026-09-04
- Status: Accepted
- Decision: Versioned regex/template parsing, merchant normalization, category
  rules, and user overrides are the MVP intelligence layer.
- Reason: They are testable, explainable, private, and work offline.
- Consequence: Unsupported templates enter review/ignored states rather than
  being sent to a service.

## D-008 — Room/SQLite as the local source of truth

- Date: 2026-09-04
- Status: Accepted and validated
- Decision: Use Room 3 KMP with bundled SQLite for the shared schema, DAO, and
  repository. Platform source sets own database-path construction.
- Reason: Structured queries, transactions, migrations, and observable data fit
  the import and dashboard requirements.
- Consequence: Versioned JSON schemas are exported from KSP. Every future schema
  version must add a migration and a test starting from the prior fixture.

## D-009 — Reconciliation complements live SMS receipt

- Date: 2026-09-04
- Status: Accepted
- Decision: Use live Android receipt for responsiveness, plus idempotent inbox
  reconciliation on foreground/resume for reliability.
- Reason: Broadcast/lifecycle conditions can cause a live event to be missed.
- Consequence: Both paths use the same importer and database uniqueness rules.

## D-010 — Monday-start local calendar reporting

- Date: 2026-09-04
- Status: Accepted for India-first MVP
- Decision: Weeks start Monday at 00:00 in the device's current time zone; months
  are local calendar months.
- Reason: Produces understandable calendar reporting and deterministic boundaries.
- Consequence: Changing time zone can regroup boundary transactions; the behavior
  must be tested and explained rather than storing a permanent derived week key.
