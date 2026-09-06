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

## D-011 — Durable import state with bounded foreground reconciliation

- Date: 2026-09-05
- Status: Accepted and validated
- Decision: Persist one coarse import-state row and, after initial completion,
  reconcile from five minutes before the last successful scan whenever the app
  resumes with SMS permission.
- Reason: Onboarding, failure recovery, and restart behavior must survive process
  death, while a small overlap prevents boundary races without rescanning all
  three months.
- Consequence: Overlap reads rely on repository idempotency. Persisted state may
  contain counts and failure codes only; message content and sensitive fields
  remain excluded.

## D-012 — Versioned, explicit parser outcomes

- Date: 2026-09-05
- Status: Accepted and validated
- Decision: Parser version 2 returns one of `Accepted`, `NeedsReview`, or
  `Rejected`. Review and rejection reasons are coarse enums safe for aggregate
  diagnostics; only accepted and reviewable transactions enter the ledger.
- Reason: A nullable result cannot distinguish harmless non-financial text from
  malformed, incomplete, or ambiguous financial messages, making behavior hard
  to test and explain.
- Consequence: Ambiguous records remain visible with low confidence and explicit
  review reasons; conflicting amounts or directions default to excluded from
  spend until confirmed. Re-import updates detected parser-v2 fields but repository
  upsert must preserve user overrides. Room v3 stores the parser version and
  per-transaction review reasons; aggregate rejection-reason counts remain
  attempt-local.

## D-013 — Explicit category precedence and merchant-rule application

- Date: 2026-09-05
- Status: Accepted and validated
- Decision: Effective category precedence is transaction override, then an
  explicitly user-approved normalized-merchant rule applied during import/reparse,
  then versioned built-in rules, then `Other`. Merchant-wide rules are created only
  by a separate explicit action; changing one transaction does not create a rule.
- Reason: This keeps automatic behavior deterministic and prevents a correction
  from unexpectedly changing other records without clear user intent.
- Consequence: Deleting a merchant rule does not erase transaction overrides.
  Existing detected categories change when their source is reparsed; parser output
  advances to version 3 for the new category policy. `Other` is reviewable but does
  not by itself exclude an otherwise valid purchase from totals.

## D-014 — Persist transaction review reasons

- Date: 2026-09-06
- Status: Accepted and validated
- Decision: Persist only coarse transaction-level review reasons as enum names.
  A merchant rule removes `UNKNOWN_CATEGORY` while preserving unrelated ambiguity;
  fee kind remains higher priority than every merchant rule.
- Reason: Confidence alone cannot distinguish resolved category uncertainty from
  unrelated amount, direction, kind, or merchant concerns after a Room round-trip.
- Consequence: Room schema version 3 adds `reviewReasons` with an empty migration
  default. No raw message content or identifiers are introduced.

## D-015 — Ledger editing and review visibility

- Date: 2026-09-06
- Status: Accepted and validated
- Decision: MVP-06 edits only category and inclusion overrides. Reset clears both
  overrides. Filtering uses effective values, calendar date bounds, currency, and
  detected review state. Detail stays open after a row leaves the filtered list.
- Reason: Corrections must be auditable and preserve parsed facts through reparse.
- Consequence: Review means any stored reason, unknown kind, or confidence below
  80%; this is a presentation threshold, not a parser acceptance change. Overrides
  do not dismiss original review concerns. Source viewing remains deferred pending
  provider/privacy validation; this version displays parsed fields only. Merchant
  rule management remains a repository capability with no automatic UI creation.

## D-016 — Weekly/monthly aggregation and comparison rule

- Date: 2026-09-06
- Status: Accepted and validated
- Decision: MVP-07 aggregates the observed parsed ledger in memory with shared
  pure functions. Weeks run Monday 00:00 through the next Monday 00:00; months
  are local calendar months, both computed from an injected instant and the
  device time zone. The headline comparison uses the same number of elapsed
  days in the immediately previous period (on a Wednesday the comparison is the
  previous Monday-through-Wednesday; month comparisons cap at the previous
  month's end). The percentage is the nearest-integer rounding of
  `(current − previous) × 100 / previous` and is null when the previous baseline
  is zero. Tapping a category or the excluded/foreign/review count opens
  Transactions with the same period range plus the matching dimension;
  `TransactionFilter` gains a default-false `foreignOnly` field.
- Reason: Uses auditable stored records, keeps a partial current period
  comparable like-for-like, and keeps all money math in exact minor units.
- Consequence: Changing time zone recomputes ranges deterministically from the
  same stored data without data loss or schema change. Existing D-010 boundary
  rules are unchanged. The previous-period comparison is display-only and never
  feeds monetary totals.

## D-017 — Expose on-demand source-message viewing

- Date: 2026-09-06
- Status: Accepted
- Decision: The transaction detail screen gains an explicit `View source
  message` action. On user action only, the app resolves the persisted SMS
  provider row ID against the system inbox and shows sender, timestamp, and
  body in an ephemeral dialog. The body is held only while the dialog is open,
  is cleared on dismiss, selection change, and detail close, and is never
  persisted, cached, or logged. Missing rows, missing provider ID, revoked
  permission, and provider failures map to explicit safe explanations.
- Reason: Users need the raw message to decide which parsed fields to correct;
  the action is user-initiated, so it stays within the source-lookup policy in
  `DATA_AND_PRIVACY.md`.
- Consequence: The `SmsSourceLookup` Android adapter queries the provider at
  display time and cannot repair a deleted message; the parsed record keeps
  working regardless. Demo/imported rows without a real provider row show the
  unavailable explanation. No manifest or schema change is required.

## D-018 — Review resolution on save and missing-merchant treatment

- Date: 2026-09-06
- Status: Accepted
- Decision: A manual save can only resolve the category concern. Saving an
  explicit category drops `UNKNOWN_CATEGORY`. Every other stored reason
  (`CONFLICTING_AMOUNTS`, `CONFLICTING_DIRECTIONS`, `UNKNOWN_KIND`) survives the
  save, and confidence is recomputed from the surviving reasons (0.35 for
  conflicts, 0.60 for other reasons, 0.90 when none remain). A missing merchant
  is not a review concern: the parser no longer emits `MISSING_MERCHANT`,
  `needsReview` ignores rows whose only stored reason is `MISSING_MERCHANT`, and
  unknown kind is represented solely by `UNKNOWN_KIND` instead of a second kind
  check. The merchant regex additionally accepts an `on X` anchor with
  digit/possessive guards. The detail screen omits the review line when nothing
  needs review.
- Reason: Review noise came almost entirely from the intentionally narrow
  merchant detection. A save only proves the category choice, so it must close
  only that concern; amount, direction, and type ambiguity need their own
  resolution later.
- Consequence: This supersedes the relevant parts of D-015: saving a category
  resolves the category review concern instead of leaving review open, while
  other concerns stay visible. Existing rows with only `MISSING_MERCHANT` leave
  review automatically. The `TransactionReviewReason.MISSING_MERCHANT` enum
  remains for stored-data compatibility. The review system is removed from all
  UI surfaces (list labels, detail flags, dashboard count, filter option) until
  a full-transaction edit can resolve every concerned field; parser reasons,
  stored reasons, and the `needsReview` rule stay intact in the data layer.
  The reset-to-defaults action is removed: editors start from the effective
  (detected) values, a null category changes nothing about review, and no
  reason-restoration logic exists.

## D-019 — Single-value transaction model

- Date: 2026-09-06
- Status: Accepted
- Decision: Every transaction field has exactly one value. The detected/user
  override split (userCategory/userIncludedInSpend/detectedIncludedInSpend) is
  removed from the model, Room schema, and UI. Parser values populate each field
  on import; the user can later change them; a re-import of the same source
  message overwrites them with fresh parser values. Spend inclusion is a
  non-null boolean rendered as a toggle at the top right of transaction detail.
- Reason: Two values per field added complexity without user benefit; the
  product direction is simple, editable facts.
- Consequence: Room schema version 4 renames detectedCategory to category, folds
  inclusion from the effective override, and drops the override columns; version
  5 adds a `userEdited` flag (both migrations tested from version 1). Imports
  stay idempotent: a duplicate broadcast or reconciliation overlap of the same
  source (fingerprint or provider ID) never duplicates a row, untouched rows are
  refreshed by a re-import so an improved parser can re-derive fields, and
  user-edited rows are frozen and never overwritten. Merchant rules apply only
  to rows being refreshed or first inserted. Uniqueness is enforced by Room
  UNIQUE indexes on `(sourceType, sourceProviderId)` and
  `(sourceType, sourceFingerprint)`. D-013/D-015 override-preservation semantics
  no longer apply.
