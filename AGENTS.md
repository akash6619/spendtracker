# SpendTracker agent guide

This file is the entry point for any coding agent working in this repository.
The repository is an Android-first, offline expense tracker that derives
transactions from financial SMS messages.

## Required reading order

Before changing code, read:

1. `docs/STATUS.md` — what exists, what is active, and the next recommended slice.
2. `docs/MVP_PLAN.md` — ordered feature slices and their acceptance gates.
3. `docs/ARCHITECTURE.md` — module boundaries and intended dependency flow.
4. `docs/DATA_AND_PRIVACY.md` — non-negotiable SMS, storage, and currency rules.
5. `docs/TESTING.md` — required automated and manual validation.
6. `docs/DECISIONS.md` — decisions that must not be casually reversed.

Use `docs/PRODUCT.md` for product intent and `docs/DEVELOPMENT.md` for setup and
commands. `docs/README.md` is the complete documentation index.

## Product invariants

- MVP is Android-only and works without login, backend, or network access.
- Initial history import covers the previous three calendar months.
- SMS bodies are processed ephemerally and must not be persisted or logged.
- Persist parsed transaction fields, an Android provider row ID where available,
  and a non-reversible source fingerprint for deduplication.
- INR is the reporting currency for MVP. Foreign-currency transactions retain
  their original amount/currency and remain outside INR aggregate totals.
- Credits, refunds, transfers, and ATM withdrawals are visible records but are
  not ordinary spend. Fees and purchase debits are included by default.
- Parsing and categorization are deterministic and on-device for MVP.
- The application manifest must not gain `INTERNET` permission during MVP.
- Test fixtures must be synthetic or irreversibly anonymized. Never commit a
  real SMS body, phone number, account number, card number, or user name.

## Work-selection protocol

1. Follow an explicit user-assigned slice. Otherwise select the first `Ready`
   item in `docs/MVP_PLAN.md` whose dependencies are complete.
2. Before implementation, add the slice and task/agent identifier to the
   `Active work` table in `docs/STATUS.md`, then mark the slice `In progress`.
3. Keep one change focused on one slice unless a dependency must be fixed.
4. Meet every acceptance criterion and test gate listed for that slice.
5. Update `docs/STATUS.md`, the slice status, and any affected architecture or
   decision documentation before handing off.
6. Record unresolved decisions under `Open questions` in `docs/STATUS.md`; do
   not invent product policy when it changes stored data or user-visible totals.

If another active entry overlaps the same files or slice, coordinate before
editing. Do not overwrite unrelated working-tree changes.

## Repository boundaries

- `shared/src/commonMain`: portable domain models, parsing, categorization,
  aggregation, repository contracts, and use cases.
- `shared/src/commonTest`: portable deterministic tests.
- `androidApp`: Compose UI, Android lifecycle/permissions, SMS adapters,
  notifications, and platform wiring.
- Build outputs under any `build/` directory are generated and must not be
  edited.

UI composables must not query the SMS provider or database directly. Android
SMS APIs stay in `androidApp`; portable business rules stay in `shared`.

## Code documentation principles

- Every new production class, interface, object, enum, and substantial state
  holder must have class-level KDoc.
- Class-level KDoc should explain the type's responsibility, what it currently
  performs, its main input/output or collaborators, and important privacy,
  persistence, threading, or lifecycle boundaries.
- Prefer two to five focused lines or a short paragraph. Use additional detail
  only when the type owns a non-obvious invariant or workflow.
- Document current behavior. Do not promise unimplemented features; mention a
  planned limitation only when readers could otherwise misunderstand the type.
- Method and inline comments should explain why, invariants, ordering, fallbacks,
  or surprising edge cases. Do not restate syntax or narrate obvious assignments.
- Update or remove comments whenever behavior changes. Stale documentation is a
  defect and must not survive the same change that made it inaccurate.
- Keep privacy-sensitive examples synthetic and never place real SMS content or
  identifiers in KDoc, comments, samples, or TODOs.

## Minimum validation

For a normal feature change, run:

```shell
./gradlew :shared:jvmTest :androidApp:testDebugUnitTest
./gradlew :androidApp:lintDebug :androidApp:assembleDebug
```

Run connected Compose/instrumentation tests when the slice adds or changes UI,
permissions, the SMS provider adapter, Room, a receiver, or WorkManager.
See `docs/TESTING.md` for the full matrix.

## Definition of done

A slice is done only when:

- its acceptance criteria are satisfied;
- relevant automated tests pass;
- required emulator/device checks pass;
- no SMS content or sensitive identifiers appear in logs or fixtures;
- user-visible empty, loading, success, denied, and error states are handled;
- accessibility labels and basic screen-reader navigation are present;
- documentation and `docs/STATUS.md` reflect the new reality.
- new or materially changed production types have accurate responsibility KDoc,
  and non-obvious rules are documented without cluttering straightforward code.
