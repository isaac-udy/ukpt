# Test categories

Every test class now carries one category annotation from `:platform:common:testing`
(`@UnitTest`, `@SubcutaneousTest`, `@ComponentTest`, `@SnapshotTest`, `@WiringTest`,
`@AdapterTest`, `@CheckTest`, `@EndToEndTest`). A new `Testing` RuleGroup governs test sources,
which the architecture rules previously excluded. JVM tests run on JUnit 5, and each category has
a Gradle task (`./gradlew unitTest`).

The module, the conventions (`ukpt.test-categories`, applied by `ukpt.jvm-base` and
`ukpt.kmp-library`), the catalog entries and the rules arrive with the file sync. Project tests
need the annotation, and some need moving or converting.

New rules: `Testing.oneCategoryPerTestClass`, `Testing.testFunctionsAssert`, a `placement` rule per
category, `Testing.UnitTest.noInfrastructure`, `Testing.UnitTest.oneProductionClass`,
`Testing.ComponentTest.rendersScreenContentOnly`, `Testing.SnapshotTest.usesTheSnapshotHarness`,
`Testing.WiringTest.resolvesTheGraph`, `ModuleRules.testSupportOnlyFromTests`, and
`architecture.everyTestDeclarationBelongsToATestConstruct`. No rule ID is renamed or removed.

## Detection

```bash
# Test classes, to annotate
grep -rlE '@Test\b|PreviewSnapshotTestCase' --include='*.kt' app feature platform | grep '/src/[^/]*[Tt]est/' | grep -v '/build/'

# JUnit 4 outside androidHostTest
grep -rnE 'import org\.junit\.(Test|Before|After|BeforeClass|AfterClass|Assert)' --include='*.kt' app feature platform \
  | grep -v '/androidHostTest/' | grep -v '/build/'

# JUnit 4 test dependencies
grep -rn 'kotlin.testJunit\b\|kotlin-test-junit"' --include='*.kts' app feature platform

# The scope canary the engine check replaces
find . -name 'ScopeExclusionCanaryRepository.kt' -not -path '*/build/*'
```

## Migration

1. Add `implementation(projects.platform.common.testing)` to every module's `commonTest`
   dependencies (multiplatform) or `testImplementation(projects.platform.common.testing)` (JVM).
   Replace `libs.kotlin.testJunit` with `libs.kotlin.test`.
2. Annotate each test class with its category. The `ukpt-testing` skill's table decides; the
   template's own tests are annotated as examples. A ViewModel test with fakes is `@UnitTest` until
   the project has a `:feature:<name>:tests` module.
3. Convert JUnit 4 tests outside `androidHostTest`: `org.junit.Test` → `kotlin.test.Test`,
   `org.junit.Assert.*` → `kotlin.test.assert*` (note the message moves to the last argument),
   `@BeforeClass`/`@AfterClass` → `org.junit.jupiter.api.BeforeAll`/`AfterAll` (keep `@JvmStatic`).
4. Move a non-snapshot test out of `androidHostTest` into `jvmTest`. The template moved
   `DesignSystemDocImagesTest` to `platform/client/design/src/jvmTest/`.
5. Delete `ScopeExclusionCanaryRepository.kt`. The generated `scopesArePartitioned` integrity test
   now fails when the production scope contains test sources.
6. Run `verifyArchitecture` and fix each `Testing.*` failure as the `ukpt-testing` skill describes.
   A helper class in test sources that is not a test becomes a `Fake[Name]`, a top-level function,
   or a member of the test class.

## Verification

```bash
./gradlew :platform:common:architecture:verifyArchitecture
./gradlew unitTest wiringTest checkTest adapterTest componentTest
./gradlew snapshotTest --no-configuration-cache
```

Each category task's report lists the classes annotated with that category, and the module's
`test`/`jvmTest` report lists the same total as before the migration.
