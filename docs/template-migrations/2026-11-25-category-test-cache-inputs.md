# Enable configuration caching for test jobs and declare category test inputs

Paparazzi snapshot recording and verification now support Gradle's configuration cache, including
when snapshots run through `allTests` or `testCoverageReport`. JVM and Android host category
tests, including Subcutaneous tests, also use Gradle's build cache to restore test outputs.

## Detection

- Search CI workflows, scripts, run configurations and developer instructions for
  `--no-configuration-cache`. Check whether `gradle.properties` overrides the template's
  `org.gradle.configuration-cache=true`. Include aggregate jobs that run tests indirectly.
- Look for tests that read a file outside the test runtime classpath. Examples include stylesheet
  checks, documentation checks and tests that read fixtures directly from the checkout.

## Migration

### Remove the test configuration-cache workaround

After syncing the updated build conventions, drop `--no-configuration-cache` from test and
coverage commands. This includes `snapshotTest`, `recordPaparazzi`, `verifyPaparazzi`,
`testAndroidHostTest`, JVM and Android host category and Subcutaneous tasks, client `allTests`,
and `testCoverageReport`. Restore the default in `gradle.properties` if the project disabled it
for these tasks.

Apply the same edit to project-owned aggregate commands. This example keeps the other Gradle
options for a CI job that invokes a custom root `:verify` task.

```sh
./gradlew :verify --continue --no-configure-on-demand --max-workers=2
```

### Declare files read by category tests

Declare each file on every task that runs the test. A multiplatform module with a `@CheckTest`
that reads a stylesheet can configure both entry points in its build file.

```kotlin
tasks.withType<Test>().matching { it.name == "jvmTest" || it.name == "checkTest" }.configureEach {
    inputs.file(layout.projectDirectory.file("src/css/theme.css"))
        .withPropertyName("stylesheet").withPathSensitivity(PathSensitivity.RELATIVE)
}
```

## Verification

- Run the edited CI command twice in the same checkout with the same arguments and inputs,
  starting with a fresh configuration-cache entry. Look for `Configuration cache entry stored`
  on the first run and `Configuration cache entry reused` on the second. Check the expected test
  results and reports. To exercise the snapshot renderer on a cache hit, run one module's
  `verifyPaparazzi` twice with `--rerun-tasks` and check that its goldens pass both times.
- If a custom verification job clears its reporting directory before Gradle runs, repeat that
  cleanup before the second run. Check that it recreates its phase markers and summary. If the
  summary attributes failures to phases, exercise a failing dependency on both runs. Gradle skips
  configuration callbacks such as `taskGraph.whenReady` when it reuses an entry. Supply any data
  the reporter needs at execution time, for example through build service parameters.
- If a shared task fails, capture its task path, the stack trace and any Gradle configuration-cache
  report. Fix the owning UKPT convention or udytils task so downstream test jobs can keep the
  cache enabled. Fix project-owned task or reporter failures in the downstream build logic.
- For each newly declared file input, run the module's category task, edit the input and run the
  same task again with `--info`. Gradle should report the changed input and execute the test.
