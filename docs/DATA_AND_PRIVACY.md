# Data, privacy, and currency

## Privacy posture

For the MVP, "on premise" means on the user's Android device. All SMS reading,
parsing, categorization, storage, and aggregation occur locally. There is no
account, backend, telemetry SDK, advertisement SDK, or network permission.

Financial SMS is highly sensitive. Google Play treats SMS permissions as
restricted. SMS-based money management is listed as an exception use case, but
it remains subject to declaration and review. Before public distribution, review
the current [Google Play SMS and Call Log permissions policy](https://support.google.com/googleplay/android-developer/answer/10208820?hl=en)
and prepare the prominent disclosure, consent flow, privacy policy, store listing,
and Permissions Declaration Form.

## Raw SMS retention decision

Do not persist raw SMS bodies.

This is primarily a privacy and breach-impact decision, not a memory-pressure
decision. Three months of message text is usually small compared with media, but
raw messages may contain OTPs, balances, account hints, personal conversations,
and unrelated private information. Keeping them expands backup, logging,
debugging, migration, and deletion risk without being required for aggregates.

During import, hold one message body only long enough to parse and fingerprint
it, then release it. Do not put message bodies in exceptions, analytics, crash
reports, notifications, database columns, or normal debug logs.

## Referencing the original message

For an Android SMS-backed transaction, persist:

- provider source type (`ANDROID_SMS`);
- provider row ID (`Telephony.Sms._ID`) when available;
- received timestamp;
- a keyed, non-reversible fingerprint made from normalized sender, timestamp,
  and message content using an installation-local secret;
- parsed transaction fields only.

The provider row ID allows an explicit `View source message` action to query the
system SMS provider later and display the body ephemerally (implemented, D-017).
The fetched row must reproduce the stored installation-local fingerprint before
anything is shown, because Android can reuse provider row IDs after deletions.
It is not a guarantee:
the user may delete the SMS, revoke permission, replace the messaging database,
or restore app data onto another device. In those cases the parsed transaction
still works and the UI says that the source is unavailable.

The fingerprint supports deduplication without storing the body. A plain hash of
short predictable messages is easier to guess, so use a keyed digest held in
Android Keystore-backed local storage where practical. Never display the digest.

## Local schema

Room 3 KMP implements this schema. Version-1 through version-3 JSON fixtures are
exported under `shared/schemas` as the baseline for future migration tests.

### `transactions`

| Field | Purpose |
| --- | --- |
| `id` | App-generated stable ID |
| `sourceType` | `ANDROID_SMS`, with future source types possible |
| `sourceProviderId` | SMS provider row ID; nullable |
| `sourceFingerprint` | Installation-keyed dedupe value |
| `sourceReceivedAt` | Epoch milliseconds from source |
| `amountMinor` | Non-negative integer minor units |
| `currencyCode` | Original currency |
| `direction` | Debit or credit |
| `kind` | Purchase, transfer, withdrawal, refund, fee, unknown |
| `detectedCategory` | Category assigned by current rules |
| `userCategory` | Nullable explicit override |
| `merchant` | Normalized merchant when confidently extracted |
| `accountHint` | Optional masked last digits only |
| `confidence` | Parser confidence/review signal |
| `parserVersion` | Version used to produce detected fields |
| `reviewReasons` | Comma-separated privacy-safe ambiguity enums; never message text |
| `detectedIncluded` | Inclusion policy result |
| `userIncluded` | Nullable explicit inclusion override |
| `createdAt`, `updatedAt` | Local bookkeeping timestamps |

Effective category is `userCategory ?: detectedCategory`. Effective inclusion is
`userIncluded ?: detectedIncluded`. Reparsing may change detected fields but must
never overwrite user fields.

Uniqueness is enforced by Room UNIQUE indexes on `(sourceType, sourceProviderId)`
when a provider ID is present and on `(sourceType, sourceFingerprint)` as a
fallback. The repository must handle history scan and live receiver races
transactionally. Imports are idempotent: a matching source identity refreshes
only rows the user has not edited, and user-edited rows are never overwritten
(D-019).

### Supporting data

- `merchant_category_rules`: user-approved normalized merchant overrides, now
  applied during future/reparsed imports below explicit transaction overrides.
- `import_state`: initial import status, last successful reconciliation time,
  parser version, and counts. It must not contain message content.
- `settings`: onboarding state and local product preferences.

## Data lifecycle

- Import window: previous three calendar months at initial import.
- Parsed transactions: retained until the user deletes them or clears all app
  data; do not silently delete a transaction merely because its SMS aged out.
- Raw body: ephemeral for the duration of one parse only.
- Source lookup: on explicit user action only.
- Android backup: disabled for MVP, as currently declared in the manifest.
- `Delete all SpendTracker data`: clears database, rules, fingerprints, import
  state, and locally held fingerprint key, then returns to onboarding.
- Revoking SMS permission stops reads and live detection but does not silently
  erase already parsed transactions. The UI offers a separate erase action.

## Currency policy

### MVP

- Base/reporting currency is INR.
- Store every supported transaction in its original currency and minor units.
- Never combine amounts of different currencies.
- Weekly/monthly headline totals include INR records only.
- Foreign records are visible with their original amount and a clear
  `Not included in INR total` explanation.
- No network exchange-rate lookup and no implied rate based on today's market.

This avoids inaccurate historical totals and preserves offline operation. A card
issuer may later send a separate INR settlement message; deduplicating an
authorization and settlement pair is a distinct future design and must not be
implemented as arbitrary FX conversion.

### Future conversion

If conversion is added, store the original amount permanently plus rate,
provider/manual source, effective timestamp, target currency, and converted
minor amount. Historical reporting must use the stored effective rate, not
silently recalculate with a current rate. A network-based rate service requires
explicit consent and a backend/network privacy review.

## Logging and diagnostics

Allowed diagnostics are aggregate counts, durations, parser version, and
coarse error codes. Sender, body, merchant, account hint, provider ID,
fingerprint, amount, and timestamp must not be logged in production. Debug logs
must use synthetic fixtures only and be disabled from release builds.
