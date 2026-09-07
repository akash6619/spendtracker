# Signing and controlled release checklist

This gate is for a debug-friendly build handed to controlled testers and for a
Google Play permission-review submission. Public distribution is not started
until the SMS permission eligibility and declaration (see
`SMS_PERMISSIONS_DECLARATION.md`) are reviewed against current Google Play policy.

## Signing setup

Release builds read optional signing credentials from a local, git-ignored
`keystore.properties` at the repository root:

```properties
storeFile=/absolute/path/to/spendtracker-release.jks
storePassword=...
keyAlias=spendtracker
keyPassword=...
```

- Create the keystore once with `keytool` and back it up outside the repo.
- `keystore.properties` and `*.jks`/`*.keystore` are git-ignored. Never commit
  them, and never commit a real store password.
- Without the file the release APK builds unsigned
  (`androidApp-release-unsigned.apk`); with it `assembleRelease` produces a
  signed `androidApp-release.apk`.

## Pre-release checks

1. Full automated matrix (see `TESTING.md`):

   ```shell
   ./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:jvmTest \
     :androidApp:testDebugUnitTest :androidApp:lintDebug \
     :androidApp:assembleDebug :androidApp:assembleRelease
   export ANDROID_SERIAL=emulator-5554
   ./gradlew :androidApp:connectedDebugAndroidTest
   ```

2. Manual device matrix on at least one physical phone (OEM SMS provider):
   clean install, upgrade install, permission denial/revocation, process death,
   delete-all, and a real incoming financial SMS reconciliation.
3. Independent recalc of seeded weekly/monthly totals equals the dashboard.
4. Inspect Logcat, the app database, and the merged release manifest for privacy
   leaks: no raw SMS, no sender/merchant/amount/account content logged.
5. Confirm the release manifest declares only `READ_SMS` and has no `INTERNET`.

## Controlled-tester distribution

- Provide a signed release APK and/or Play internal-testing track.
- The tester cohort is small and non-public until the SMS declaration review
  completes.

## Handoff

- [ ] Keystore created and backed up; signing credentials applied
- [ ] Release APK signed and installed cleanly on a physical device
- [ ] Full matrix + manual matrix green
- [ ] Privacy inspection clean
- [ ] Store listing, privacy policy, and SMS declaration reviewed against policy
