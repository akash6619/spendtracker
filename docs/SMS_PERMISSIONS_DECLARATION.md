# Restricted SMS permissions declaration (draft)

Material to support a Google Play `READ_SMS` / SMS-based money-management
review. Confirm every point against the current Google Play SMS and Call Log
permissions policy before submission.

## Core use case

SpendTracker reads financial transaction SMS from the user's own device so it can
detect card/bank/UPI debits and show a private weekly/monthly spending summary.
This falls under the allowed SMS money-management exception, but it remains
subject to Play's review and declaration.

## Access model

- Only `READ_SMS` is declared. No `RECEIVE_SMS`, no `INTERNET`, no call-log or
  account permissions.
- Reading is scoped to the previous three calendar months for the initial import
  and to messages since the last successful scan (plus a small overlap) for
  automatic foreground reconciliation on app open.
- New-message pickup is foreground only: on Android 14+ a non-default SMS app
  cannot receive full SMS bodies in broadcasts. There is no background
  notification/observer.

## Core functionality requirement

The primary function is SMS-based expense tracking; the app does not function
without the declared permission, and it is surfaced only after a prominent
in-app disclosure and an explicit user action.

## Data use and protection

- SMS bodies are processed ephemerally for parsing and a non-reversible keyed
  fingerprint and are never persisted, logged, or transmitted.
- Parsed transaction fields and a masked account hint are stored only on-device
  in Room (SQLite). Backup is disabled.
- No login, no account, no analytics, no ads, no network access.
- Users can view a single source message on demand, revoke SMS access, or
  `Delete all SpendTracker data`.

## Prominent disclosure

In-app text (before the system permission prompt and repeated in Settings)
explains that SpendTracker reads the previous three months of financial SMS,
processes it on the device, does not save message bodies, and stores nothing off
device. See `STORE_LISTING_AND_PRIVACY.md`.

## Steps before submission

- [ ] Re-read current Google Play SMS permissions policy and the declaration
      requirements for your developer account.
- [ ] Confirm core-functionality + restricted-access framing above.
- [ ] Provide the signed build and the privacy policy URL.
- [ ] Do not start public distribution until this review completes.
