# Development guide

## Toolchain

- Android Studio Quail or another version compatible with AGP 9.4.
- JDK 17 or a newer version supported by the configured build.
- Android SDK Platform 37; minimum supported app API is 26.
- Gradle wrapper from the repository.
- Android emulator plus a physical Android device for release validation.

Versions are centralized in `gradle/libs.versions.toml`. Do not upgrade the
toolchain as an incidental part of a feature slice.

## Open and run

1. Open the repository root in Android Studio.
2. Let Gradle sync complete.
3. Ensure `local.properties` points to the local Android SDK. It is intentionally
   ignored by version control.
4. Select the `androidApp` run configuration.
5. Select an emulator/device and press Run.

For an embedded emulator, enable `Android Studio > Settings > Tools > Emulator >
Launch in the Running Devices tool window`, launch the AVD from Device Manager,
and open `View > Tool Windows > Running Devices`.

The development machine used for the initial prototype has an AVD named
`SpendTracker_API_37`; agents must not assume that name exists on every machine.

## Build and verify

```shell
./gradlew :shared:jvmTest
./gradlew :shared:jvmTest :androidApp:testDebugUnitTest
./gradlew :androidApp:lintDebug :androidApp:assembleDebug
```

The debug APK is generated at:

```text
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Use `docs/TESTING.md` for connected and manual gates.

## Coding conventions

- Kotlin source uses four-space indentation and trailing commas where idiomatic.
- Prefer immutable data and explicit sealed UI states.
- Use constructor injection; avoid service locators and global mutable state.
- Perform SMS/database work on an appropriate background dispatcher.
- Inject time for import cutoffs and aggregate tests.
- Use integer minor units for money and explicit currency codes.
- Keep Android types out of `commonMain`.
- Keep composables stateless where possible and provide previews with synthetic
  data for each important state.
- User-facing strings should move to Android string resources as the UI shell is
  refactored; do not continue expanding hard-coded text in `MainActivity`.

### Comments and KDoc

Treat code documentation as part of implementation, not a cleanup step:

- Add class-level KDoc to every production class, interface, object, enum, and
  substantial state holder introduced by a change.
- Explain responsibility, current behavior, collaborators/data flow, and any
  important privacy, persistence, threading, lifecycle, or fallback boundary.
- Keep method-level and inline comments for non-obvious reasoning: exact-money
  handling, deduplication, override precedence, rejection rules, and operation
  ordering are good examples.
- Avoid comments that repeat names or syntax. Prefer explaining why a rule exists
  and what must remain true when the code is modified.
- Review nearby comments whenever implementation changes, and update or delete
  anything that no longer describes reality.

## Feature workflow

1. Read the required context in `AGENTS.md`.
2. Claim one slice in `STATUS.md` and mark it `In progress` in `MVP_PLAN.md`.
3. Add or update tests with the behavior.
4. Implement within the architecture boundaries.
5. Run the slice gate and baseline Gradle commands.
6. Inspect the UI on emulator when relevant.
7. Update status, decisions, and documentation, then clear the active-work row.

## Debugging SMS safely

- Develop parser behavior with synthetic unit-test fixtures.
- Develop UI with a debug-only fake repository/data source.
- Use emulator-injected synthetic SMS only for the Android provider boundary.
- Never print cursor contents or message bodies to Logcat.
- Never ask a user to export an entire inbox for debugging.

If a real-device-only parser problem must be investigated, first build a local,
in-app redaction/review flow. Do not transmit or copy the original message into
issues, chat, test fixtures, or source control.
