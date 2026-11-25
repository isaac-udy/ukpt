# Unit Test data classes and grouped `:tests` paths

`Testing.UnitTest.oneProductionClass` treats a `data class`, a `value class`, or a class of
primary-constructor `val`s only as test data. The Testing rules match a `:tests` module under a
feature group directory (`feature/capture/insta/tests`).

`:platform:common:subcutaneous` has `awaitAsserted`, which retries an assertion until the
assertion passes or the timeout ends.

No rule ID is added, renamed or removed.

## Detection

```bash
# Exceptions or workarounds for the widened rule
grep -rn 'Testing.UnitTest.oneProductionClass' --include='*.kt' feature platform app
# Local copies of the new helper
grep -rn 'fun <T> awaitAsserted' --include='*.kt' feature platform app
```

## Migration

1. Apply the catalog changes through the rule sync.
2. Remove `@ArchitectureException` entries and moved tests that only worked around
   `Testing.UnitTest.oneProductionClass`. Ask the owner before removing an exception.
3. Replace local copies of `awaitAsserted` with `platform.testing.subcutaneous.awaitAsserted`.

## Verification

`./gradlew verifyArchitecture unitTest subcutaneousJvmTest` passes.
