# Product definition

## Vision

Give a person a trustworthy view of everyday spending without requiring bank
credentials, an account, or uploading their messages. The Android app detects
financial SMS locally, turns supported messages into transactions, groups
spending into understandable categories, and shows daily, weekly, and monthly trends.

The experience is inspired by SMS-based money-management apps such as Axio, but
the first release deliberately favors privacy, understandable rules, and manual
correction over broad automation.

## Primary user

An Android user in India who receives transaction alerts by SMS and wants a
quick picture of personal spending across bank accounts and cards.

## MVP user journey

1. The user opens the app without creating an account.
2. The app explains exactly why SMS access is needed and that processing stays
   on the device.
3. The user grants SMS permission and starts a three-month import.
4. The app discovers supported financial messages without retaining raw bodies.
5. The user sees imported transactions, excluded records, and uncertain items.
6. The user corrects a category or marks a record as included/excluded.
7. The dashboard shows this week and this month, with comparison to the previous
   equivalent period and a category breakdown.
8. New supported SMS messages appear automatically; a foreground reconciliation
   catches anything missed while the app was stopped or permission was absent.
9. The user can revoke access or erase all app data at any time.

## MVP functional requirements

### Import and detection

- Import inbox SMS from the previous three calendar months only.
- Explain permission use before the system prompt.
- Allow retry after denial and remain usable in an empty/manual-review state.
- Parse amount, ISO currency, debit/credit direction, transaction kind,
  merchant when detectable, timestamp, masked account hint, and confidence.
- Ignore obvious OTP, failed, declined, cancelled, and authorization-only texts.
- Make repeat scans idempotent; the same source message must not create a second
  transaction.
- Reconcile on app start/resume after the initial import.

### Transaction semantics

- Include debit purchases and fees in spend by default.
- Record but exclude credits, refunds, transfers, and cash withdrawals by
  default. A user override always wins.
- Preserve foreign transactions in their original currency but exclude them
  from INR totals in MVP.
- Show why a transaction is excluded or considered uncertain.

### Categories

The initial buckets are:

- Food & dining
- Groceries
- Transport
- Shopping
- Bills & utilities
- Housing
- Health
- Entertainment
- Travel
- Education
- Subscriptions
- Fees & charges
- Other

Rules run locally. A manual category override must persist across reparsing.
Optionally, a user may apply the corrected category to the same normalized
merchant in future messages.

### Views

- Dashboard with this-week and this-month spend.
- Previous-period comparison using the same local calendar/time zone.
- Category breakdown with amounts and percentage of included INR spend.
- Chronological transaction list with date and category filters.
- Transaction detail/edit view with original parsed amount/currency, merchant,
  direction, kind, category, confidence/review status, and inclusion state.
- Permission denied, importing, empty, partial failure, and no-recognized-spend
  states.

### New messages

- Detect new SMS messages with Android platform APIs after permission is granted.
- Parse and save through the same idempotent pipeline used by history import.
- Refresh dashboard and transaction views without an app restart.
- Never show message bodies in a notification or log.

### Privacy and settings

- No login or backend.
- No network requirement or `INTERNET` manifest permission.
- No raw SMS-body persistence.
- A clear `Delete all SpendTracker data` action.
- A permission/status screen showing last successful scan and import window.
- A privacy explanation available before and after permission grant.

## Non-functional requirements

- Parsing, storage, and aggregation happen off the main thread.
- History import streams records and does not accumulate raw SMS in memory.
- Monetary arithmetic uses integer minor units; never `Float` or `Double`.
- Dashboard calculations are deterministic for a supplied clock and time zone.
- A process interruption or repeated broadcast cannot duplicate transactions.
- Supported core logic remains in Kotlin Multiplatform common code.
- The app supports Android API 26 through the configured target SDK.
- Core screens support font scaling, TalkBack labels, and sufficient contrast.

## Explicitly outside the MVP

- Login, cloud sync, multi-device sync, or a server.
- Bank account, email, notification, UPI app, or statement integrations.
- Budgets, bill reminders, subscriptions forecasting, net worth, investments,
  credit score, lending, or advertisements.
- Automatic live foreign-exchange conversion.
- User-created category taxonomy or transaction splitting.
- Receipt scanning, OCR, or generative/remote ML classification.
- Full iOS SMS parity.

## Success criteria

The MVP is releasable for controlled testing when:

- every slice in `MVP_PLAN.md` is `Done`;
- known supported synthetic parser fixtures meet their expected outcome;
- all automated and manual release gates in `TESTING.md` pass;
- repeated three-month imports and live/reconcile overlap create no duplicates;
- no raw SMS content is retained or emitted in logs;
- INR dashboard totals can be independently reproduced from stored records;
- the permission disclosure and Google Play SMS-permission declaration material
  are ready for review.

Product-market metrics such as retention are deliberately deferred until a
privacy-preserving analytics strategy is explicitly approved.

