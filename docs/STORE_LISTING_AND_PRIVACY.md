# Store listing and privacy policy (draft)

Controlled-release copy for Google Play. Reuse the exact behavior described here
so disclosure always matches the app.

## Short description

Private on-device expense tracking from your financial SMS.

## Full description

SpendTracker turns the financial SMS on your own phone into a clear weekly and
monthly view of your spending — entirely on your device, with no account, no
login, and no internet access.

How it works
- With your permission, SpendTracker reads the previous three calendar months of
  messages from your inbox on this device.
- It detects supported card, bank, and UPI transaction alerts, extracts the
  amount, currency, merchant, and category, and stores parsed records locally.
- New financial messages are picked up automatically the next time you open the
  app. You can correct a category or include/exclude any transaction.

Privacy
- Message bodies are read only to recognize a transaction and are never saved,
  logged, or sent anywhere.
- Parsed transactions live only on this device; app backup is disabled.
- No analytics, ads, or tracking SDKs. SpendTracker never asks for your account,
  credentials, or OTPs.
- You control your data: revoke SMS access in system Settings to stop scanning,
  or use Delete all SpendTracker data in Settings to remove everything stored.

Important
- Only `READ_SMS` is requested, for the money-management function described.
- Foreign-currency records keep their original currency and are not converted.
- This is not a bank, and SpendTracker does not have access to your accounts.

## Privacy policy

Effective: pending controlled release.

SpendTracker ("the app") is an offline expense tracker. This policy explains what
the app does with data.

What the app accesses
- SMS inbox, read permission. The app reads financial transaction SMS from the
  previous three calendar months (initial) and, after that, messages since the
  last successful scan, each time you open the app.

What the app stores
- Parsed transaction details only: amount and currency, direction, type,
  category, normalized merchant, a masked account ending, timestamp, parser
  confidence/version, and review markers. Plus app settings and import status.
- A non-reversible, device-local fingerprint per message used to avoid duplicates.
- Nothing is stored for SMS you have not granted access to.

What is never stored or shared
- SMS bodies and full sender identities are processed in memory for parsing only
  and are not persisted, logged, or transmitted.
- There is no account, no cloud, no analytics, no ads, and no network permission,
  so nothing can be sent off the device.

Your controls
- Revoke SMS access any time in system Settings; stored data stays until you
  delete it.
- Delete all SpendTracker data in Settings removes every stored transaction,
  rule, import status, and the local fingerprint key.

Contact: see the store listing developer contact.

## Notes

- Backup is disabled via the manifest; data-extraction rules declare no
  cloud-transfer.
- Public release begins only after the Google Play SMS permission declaration
  review completes (see `SMS_PERMISSIONS_DECLARATION.md`).
