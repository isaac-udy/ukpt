# Subcutaneous Tests run on the JVM, Android host, iOS simulator and web hosts

`ukpt.feature-tests` builds each `:feature:<name>:tests` module as a Kotlin Multiplatform module.
The tests live in `src/commonTest` and run on each host listed in the `ukpt.subcutaneousHosts`
Gradle property (default `jvm,android`). A `gradle.properties` in a `:tests` module's directory
overrides the list for that module. The `subcutaneousTest` task no longer exists; each host has
one task:

| Host | Task |
| --- | --- |
| `jvm` | `subcutaneousJvmTest` |
| `android` | `subcutaneousAndroidHostTest` |
| `ios` | `subcutaneousIosSimulatorArm64Test` |
| `wasmJs` | `subcutaneousWasmJsTest` |
| `js` | `subcutaneousJsTest` |

The in-process server and embedded Postgres run on the JVM, so a `:tests` module that depends on a
`:server` or on `:platform:common:subcutaneous-server` fails configuration when its list contains
`ios`, `wasmJs` or `js`. A project without a desktop client sets `ukpt.subcutaneousHosts=android`.

Source sets of a `:tests` module:

| Contents | Before | After |
| --- | --- | --- |
| Test graph, scenarios, fakes, fixtures, robots | `src/main` | `src/commonMain` |
| Subcutaneous Tests | `src/test` | `src/commonTest` |

Rule changes:

- `Testing.SubcutaneousTest.placement` requires `src/commonTest`, in a `:feature:<name>:tests`
  module or in `:platform:common:subcutaneous` for the harness's own tests; `Testing.Robot.placement`
  requires `src/commonMain`.
- `Testing.SubcutaneousTest.runsInTheHarness` requires an expression body,
  `fun `…`() = subcutaneous(graph) { … }`.
- New: `Testing.SubcutaneousTest.testsModuleHoldsOnlySubcutaneousTests`.
- New construct `Testing.HostEdges` (`.placement`, `.naming`): an `Edges.() -> Unit` passed to
  `SubcutaneousGraph(hostEdges = …)` that replaces bindings one host cannot build, such as a
  binding that needs an Android `Context`.

`:platform:common:subcutaneous` and `:platform:common:robots` are Kotlin Multiplatform modules,
so the harness and robots compile for every target. `subcutaneous(…)` returns
`kotlinx.coroutines.test.TestResult` from `runTest`: on JS and wasm the result is the Promise the
test runner waits for. `runTest` fails a test when a coroutine throws an uncaught exception, such
as a `viewModelScope` launch that throws; `runBlocking` only printed the exception. `featureClient(…)` moved from `:platform:common:subcutaneous-server` to
`:platform:common:subcutaneous`, in the same package. `SubcutaneousServer.run` takes
`edges: List<Module>`. On the Android host the category annotations apply JUnit 5 tags and
timeouts, as on the JVM.

## Detection

Every project with a `:feature:<name>:tests` module is affected:

```bash
find feature -type d -path '*/tests/src/*' \( -name main -o -name test \) -prune -print
grep -rn "subcutaneousTest\b" --include='*.yml' --include='*.yaml' --include='*.kts' --include='*.md' --include='*.sh' .
```

## Migration

1. Add `ukpt.subcutaneousHosts=jvm,android` to `gradle.properties`, or `android` when the project
   has no desktop client. A `:tests` module without a server dependency may add `ios`, `wasmJs`
   and `js` in a `gradle.properties` in its directory.
2. In each `:feature:<name>:tests` module:
   - `git mv src/main/kotlin src/commonMain/kotlin`.
   - `git mv src/test/kotlin src/commonTest/kotlin`.
   - Move the `dependencies { }` block of `build.gradle.kts` into
     `kotlin { sourceSets { commonMain.dependencies { … } } }`. `:feature:core:tests` shows the
     result.
3. Rewrite each `@Test` function of a Subcutaneous Test with a block body as
   `fun `…`() = subcutaneous(graph) { … }`.
4. Move each test without `@SubcutaneousTest` out of the `:tests` module, to the module its
   category names.
5. Replace `subcutaneousTest` with the task of each host the project lists in CI workflows and
   scripts. `subcutaneousIosSimulatorArm64Test` runs on a macOS runner. The template's
   `.github/workflows/ci.yml` also runs the harness's own tests on wasm, JS and the iOS simulator.
   A project without a server that builds its graph with `listOf(networkDependencies, …)` may
   switch to `featureClient(…)`.
6. A robot that calls a desktop-only API fails to compile for the Android target. Replace the
   call with the common Compose UI test API that `ScreenRobot` uses.
7. A graph binding that the Android host cannot build (it needs a `Context`) fails on
   `subcutaneousAndroidHostTest`: the Android host runs without Robolectric. Replace the binding
   in a `[name]HostEdges` declaration; the `ukpt-testing` skill, "Host edges", shows the shape.
8. A Subcutaneous Test that now fails with an exception from a ViewModel coroutine exposes a
   ViewModel that throws in the app too. Fix the ViewModel or the test's setup.
9. Update `@ArchitectureException` annotations that name `Testing.SubcutaneousTest.placement` or
   `Testing.Robot.placement` only if the annotated declaration moved.

## Verification

```bash
./gradlew verifyArchitecture
./gradlew subcutaneousJvmTest subcutaneousAndroidHostTest
./gradlew subcutaneousCoverage
./gradlew :platform:common:subcutaneous:jsBrowserTest
```

Run only the tasks of the hosts the project lists.
