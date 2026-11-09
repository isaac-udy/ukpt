---
name: ukpt-testing
description: >-
  Write, place, and run UKPT tests by category — Subcutaneous, Unit, Component,
  Snapshot, Wiring, Adapter, Check, End-to-end — with the category annotations
  from :platform:common:testing, the per-category Gradle tasks, and the Testing
  architecture rules that check a test behaves as its category claims. Use when
  adding or changing a test, choosing what kind of test to write, running one
  category, or fixing a Testing.* rule failure.
---

# ukpt-testing

Every test class carries exactly one annotation from `platform.testing`. The category says what
the test may use besides the code under test; the `Testing` rules check it
(`platform/common/architecture/docs/testing.md`).

## Choosing a category

| Write a… | When | Where |
| --- | --- | --- |
| `@SubcutaneousTest` | Behaviour a user reaches through a screen, or a `@Urpc` service no screen calls. **The default.** | `:feature:<name>:tests`, `src/test/` |
| `@UnitTest` | Logic with many inputs: parsing, validation, formatting, a branching UseCase | the module's `commonTest` (or a platform test set) |
| `@ComponentTest` | A Compose HTML `[Name]HtmlScreenContent`, with fixed state | the `:client` module's `src/jsTest/` |
| `@SnapshotTest` | The module's one `PreviewSnapshotTest` | `src/androidHostTest/` |
| `@WiringTest` | An app shell's Koin graph (`verify()`) | the `:app` module's test set |
| `@AdapterTest` | One server adapter against real infrastructure, where a Subcutaneous Test reaches poorly | a server module's `src/test/` |
| `@CheckTest` | Two committed files that must agree (CSS tokens, doc images) | a JVM test set |
| `@EndToEndTest` | A journey through the desktop app's UI | `:app:client:desktop`, `src/test/` |

Ask first whether a Subcutaneous Test covers the behaviour. A Unit Test constructs one production
class (`Testing.UnitTest.oneProductionClass`) and imports no DI, network, database, file or UI-test
API (`Testing.UnitTest.noInfrastructure`); collaborators are lambdas for `fun interface`s or
`Fake[Name]` classes. Constructing domain models, states and other value types is test data and
does not count.

## Writing one

Copy the nearest test of the same category:

- Unit: `feature/core/client/src/commonTest/kotlin/feature/ukpt/client/ui/AsyncStateLoadTest.kt`
- Component: `feature/core/client/src/jsTest/kotlin/feature/ukpt/client/ui/UkptHtmlScreenContentTest.kt`
- Snapshot: `feature/core/client/src/androidHostTest/kotlin/platform/snapshot/PreviewSnapshotTest.kt`
- Wiring: `app/server/src/test/kotlin/com/isaacudy/ukpt/ServerDependenciesTest.kt`
- Adapter: `platform/server/postgres/src/test/kotlin/platform/server/postgres/TransactionRunnerTest.kt`
- Check: `platform/client/design-html/src/jvmTest/kotlin/platform/design/html/UkptDesignCssTest.kt`

The module's test source set needs `projects.platform.common.testing` (in `commonTest` for a
multiplatform module, `testImplementation` for a JVM module) and `libs.kotlin.test`. JVM tests run
on JUnit 5; use `kotlin.test` assertions and `org.junit.jupiter.api` lifecycle annotations, never
`org.junit.Test`. Only `androidHostTest` runs JUnit 4, for Paparazzi.

Every `@Test` function asserts (`Testing.testFunctionsAssert`): an `assert*`, `fail`, `verify`,
`expect*` or `await*` call, directly or through a helper declared in the same file. Every other
top-level declaration in test sources is a `Fake[Name]` class or object, a top-level fixture
function or property, or a `@Composable` snapshot surface in `androidHostTest`.

## Running a category

A task name given without a module path runs in every module that has it:

```
./gradlew unitTest
./gradlew subcutaneousTest
./gradlew componentTest     # also runs each :client module's jsTest
./gradlew wiringTest checkTest adapterTest endToEndTest
./gradlew snapshotTest --no-configuration-cache
```

One module: `./gradlew :feature:core:client:unitTest`. On the JVM the category annotations carry
JUnit 5 tags and timeouts (10 s unit; 30 s component, wiring, check; 60 s adapter, subcutaneous;
120 s end-to-end). kotlin.test has no tags on JS, wasm or iOS, so those targets run through their
own tasks (`jsTest`, `wasmJsTest`, `iosSimulatorArm64Test`). `test`/`jvmTest` still run every
category.

## Rule failures

`./gradlew :platform:common:architecture:verifyArchitecture` checks every rule. A `Testing.*`
failure names the rule; its statement and rationale are in
`platform/common/architecture/docs/testing.md`.

- `Testing.oneCategoryPerTestClass` — add the one annotation.
- `architecture.everyTestDeclarationBelongsToATestConstruct` — a helper class needs to be a
  `Fake[Name]`, a top-level function, or part of the test class.
- `Testing.*.placement` — move the file to the source set the table above names.
- `Testing.ComponentTest.rendersScreenContentOnly` — a ViewModel or repository in a Component Test
  makes it a Subcutaneous Test.
