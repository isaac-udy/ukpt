# Snapshot handler plain-test safety

The directory-grouped Paparazzi handler now keeps its three execution modes distinct:

- `recordPaparazzi` writes committed goldens;
- `verifyPaparazzi` compares against committed goldens and fails on unacceptable differences;
- a plain host-test run writes a report artifact under `build/paparazzi/` without changing goldens.

Previously, the plain-test path called the record implementation and could silently overwrite a
committed golden when UI changed. The failure message also displayed Paparazzi's default `0.01%`
threshold as `1%`, although the comparison itself used the correct upstream units.

## Detection

A project is affected if a client module's `DirectorySnapshotHandler.kt` has an `else` branch that
calls `record(golden, image)`, or formats the configured tolerance as
`maxPercentDifference * 100`.

## Migration

1. Replace `DirectorySnapshotHandler.kt` in every feature client module with the current template
   version from
   `feature/core/client/src/androidHostTest/kotlin/platform/snapshot/DirectorySnapshotHandler.kt`.
2. Copy `DirectorySnapshotHandlerTest.kt` from the same template directory into every module that
   owns a copy of the handler, or move the handler and its tests into shared test infrastructure.
3. Ensure all `verifyPaparazzi` and `recordPaparazzi` commands use
   `--no-configuration-cache`.

## Verification

Run the plain host test and confirm it leaves the working tree clean, then run snapshot verification:

```bash
./gradlew :feature:<name>:client:testAndroidHostTest --no-configuration-cache
git diff --exit-code
./gradlew :feature:<name>:client:verifyPaparazzi --no-configuration-cache
```

The handler unit tests must pass, and an intentional snapshot difference should report a default
tolerance of `0.0100%`.
