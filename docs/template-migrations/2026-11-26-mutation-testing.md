# `runMutants` audits the tests with mutation testing

The `ukpt.mutation` convention plugin (in `build-logic`) applies the udytils mutation plugin to the
root build. `./gradlew runMutants` compiles mutants into the JVM code, runs each selected mutant
against the tests of the categories that the mutant's Construct expects, and writes
`build/reports/mutation/results.json`. `testCoverageReport` shows the results when the file exists.
The `ukpt-test-audit` skill covers running the audit and triaging the results.

- `settings.gradle.kts` substitutes the four udytils mutation modules.
- `gradle/libs.versions.toml` adds `udytils-mutationGradlePlugin`.
- The root `build.gradle.kts` adds the plugin to the buildscript classpath, applies
  `id("ukpt.mutation")` and declares the catalog rules in a `mutation { }` block.

## Detection

```bash
grep -n 'id("ukpt.mutation")' build.gradle.kts
```

A project is affected if the command prints nothing.

## Migration

1. In `settings.gradle.kts`, add these lines to the `dependencySubstitution` block of
   `includeBuild("embedded-udytils")`:
   ```kotlin
   substitute(module("dev.isaacudy.udytils:mutation-runtime")).using(project(":mutation-runtime"))
   substitute(module("dev.isaacudy.udytils:mutation-compiler-plugin")).using(project(":mutation-compiler-plugin"))
   substitute(module("dev.isaacudy.udytils:mutation-junit-driver")).using(project(":mutation-junit-driver"))
   substitute(module("dev.isaacudy.udytils:mutation-gradle-plugin")).using(project(":mutation-gradle-plugin"))
   ```
2. In `gradle/libs.versions.toml`, add under `[libraries]`:
   ```toml
   udytils-mutationGradlePlugin = { module = "dev.isaacudy.udytils:mutation-gradle-plugin" }
   ```
3. In the root `build.gradle.kts`, add `classpath(libs.udytils.mutationGradlePlugin)` to the
   `buildscript { dependencies { } }` block, add `id("ukpt.mutation")` after
   `id("ukpt.test-coverage")` in the `plugins { }` block, and add the catalog after the
   `testCoverage { }` block:
   ```kotlin
   mutation {
       rule("domain") { constructs("ClientDomain.*", "ServerDomain.*", "FeatureRules.Shared*") }
       rule("data") { constructs("ClientData.*", "ServerData.*") }
       rule("view-models") { constructs("ClientUi.ViewModel", "ClientUi.ViewModelState") }
       rule("server-services") { constructs("ServerServices.*") }
   }
   ```
   A project that removed one of these Construct groups deletes the matching rule.

## Verification

```bash
./gradlew runMutants --max-workers=2
```

The run prints `build/reports/mutation/summary.txt` with a count of killed, survived and uncovered
mutants. A first run compiles every JVM module again and takes several minutes.
