# Subcutaneous Tests run on the JVM and the Android host

`ukpt.feature-tests` builds each `:feature:<name>:tests` module as a Kotlin Multiplatform module.
The tests live in `src/commonTest` and run on each host listed in the `ukpt.subcutaneousHosts`
Gradle property: `jvm`, `android` (default `jvm,android`). Each host has one task,
`subcutaneousJvmTest` and `subcutaneousAndroidHostTest`, and the `subcutaneousTest` task no longer
exists. A project without a desktop client sets `ukpt.subcutaneousHosts=android`.

Source sets of a `:tests` module:

| Contents | Before | After |
| --- | --- | --- |
| Test graph, scenarios, fakes, fixtures, robots | `src/main` | `src/commonMain` |
| Subcutaneous Tests | `src/test` | `src/commonTest` |

Rule changes:

- `Testing.SubcutaneousTest.placement` requires `src/commonTest`; `Testing.Robot.placement`
  requires `src/commonMain`.
- `Testing.SubcutaneousTest.runsInTheHarness` requires an expression body,
  `fun `…`() = subcutaneous(graph) { … }`.
- New: `Testing.SubcutaneousTest.testsModuleHoldsOnlySubcutaneousTests`.
- New construct `Testing.HostEdges` (`.placement`, `.naming`): an `Edges.() -> Unit` passed to
  `SubcutaneousGraph(hostEdges = …)` that replaces bindings one host cannot build, such as a
  binding that needs an Android `Context`.

`:platform:common:robots` is a Kotlin Multiplatform module, so robots compile for every target.
`SubcutaneousServer.run` takes `edges: List<Module>`. On the Android host the category
annotations apply JUnit 5 tags and timeouts, as on the JVM.

## Detection

Every project with a `:feature:<name>:tests` module is affected:

```bash
find feature -type d -path '*/tests/src/*' \( -name main -o -name test \) -prune -print
grep -rn "subcutaneousTest\b" --include='*.yml' --include='*.yaml' --include='*.kts' --include='*.md' --include='*.sh' .
```

## Migration

1. Add `ukpt.subcutaneousHosts=jvm,android` to `gradle.properties`, or `android` when the project
   has no desktop client.
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
5. Replace `subcutaneousTest` with `subcutaneousJvmTest` and `subcutaneousAndroidHostTest` (the
   hosts the project lists) in CI workflows and scripts.
6. A robot that calls a desktop-only API fails to compile for the Android target. Replace the
   call with the common Compose UI test API that `ScreenRobot` uses.
7. A graph binding that the Android host cannot build (it needs a `Context`) fails on
   `subcutaneousAndroidHostTest`. Replace it in a `[name]HostEdges` declaration; the `ukpt-testing`
   skill, "Host edges", shows the shape.
8. Update `@ArchitectureException` annotations that name `Testing.SubcutaneousTest.placement` or
   `Testing.Robot.placement` only if the annotated declaration moved.

## Verification

```bash
./gradlew verifyArchitecture
./gradlew subcutaneousJvmTest subcutaneousAndroidHostTest
./gradlew subcutaneousCoverage
```

Run only the tasks of the hosts the project lists.
