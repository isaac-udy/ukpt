---
name: ukpt-testing
description: >-
  Write, place, and run UKPT tests by category — Subcutaneous, Unit, Component,
  Snapshot, Wiring, Adapter, Check, End-to-end — with the category annotations
  from :platform:common:testing, the per-category Gradle tasks, the Subcutaneous
  harness (test graph, scenarios, edges, coverage), and the Testing
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

- Subcutaneous: `feature/core/tests/src/test/kotlin/feature/ukpt/tests/UkptViewModelTest.kt`
  (ViewModel entry), `GreetingServiceTest.kt` beside it (server entry)
- Unit: `feature/core/client/src/commonTest/kotlin/feature/ukpt/client/ui/AsyncStateLoadTest.kt`
- Component: `feature/core/client/src/jsTest/kotlin/feature/ukpt/client/ui/UkptHtmlScreenContentTest.kt`
- Snapshot: `feature/core/client/src/androidHostTest/kotlin/platform/snapshot/PreviewSnapshotTest.kt`
- Wiring: `app/server/src/test/kotlin/com/isaacudy/ukpt/ServerDependenciesTest.kt`
- Adapter: `platform/server/postgres/src/test/kotlin/platform/server/postgres/TransactionRunnerTest.kt`
- Check: `platform/client/design-html/src/jvmTest/kotlin/platform/design/html/UkptDesignCssTest.kt`
- End-to-end: `app/client/desktop/src/test/kotlin/com/isaacudy/ukpt/GreetingJourneyTest.kt`

The module's test source set needs `projects.platform.common.testing` (in `commonTest` for a
multiplatform module, `testImplementation` for a JVM module) and `libs.kotlin.test`. JVM tests run
on JUnit 5; use `kotlin.test` assertions and `org.junit.jupiter.api` lifecycle annotations, never
`org.junit.Test`. Only `androidHostTest` runs JUnit 4, for Paparazzi.

Every `@Test` function asserts (`Testing.testFunctionsAssert`): an `assert*`, `fail`, `verify`,
`expect*` or `await*` call, directly or through a helper declared in the same file. Every other
top-level declaration in test sources is a `Fake[Name]` class or object, a top-level fixture
function or property, or a `@Composable` snapshot surface in `androidHostTest`.

## Subcutaneous tests

The harness is `:platform:common:subcutaneous` (package `platform.testing.subcutaneous`). A
feature's `:feature:<name>:tests` module applies `ukpt.feature-tests` and depends on the harness
and the feature's `:client` and `:server` (`ukpt-feature-slice` scaffolds it). Its `src/main` holds
the test graph and the scenarios; its `src/test` holds the tests.

**Graph.** One `SubcutaneousGraph` per `:tests` module, production modules only:

```kotlin
val ukptTestGraph = SubcutaneousGraph(
    client = featureClient(ukptClientDependencies),   // + networkDependencies
    server = featureServer(ukptServerDependencies),   // + the Postgres modules
)
```

**A test.** `subcutaneous(graph) { … }` is the whole body of each `@Test`. Per test it clones an
empty database from a template migrated once per JVM (`classpath:db/migration`), starts the server
graph on Ktor's test engine, builds the client graph against it, and installs enro-test. It tears
all of it down when the block returns or throws. The block runs on `Dispatchers.Main`, a dedicated
thread; waits are real time.

```kotlin
@Test
fun `confirming the reset dialog clears the history`() = subcutaneous(ukptTestGraph) {
    given { greetingHistory("Hello", "Hello again") }
    val screen = viewModel<UkptViewModel, UkptDestination>(UkptDestination)
    screen.viewModel.state.awaitMatching { it.greetingTexts()?.size == 2 }

    screen.viewModel.onResetRequested()
    screen.navigation.assertOpened<ConfirmResetDestination>().sendCompletedForTest()

    screen.viewModel.state.awaitMatching { it.greetingTexts() == emptyList<String>() }
}
```

- `viewModel<VM, K>(key)` — the ViewModel from the client graph, with a `TestNavigationHandle` for
  `key` (`screen.navigation`; use the enro-test assertions on it).
- `state.awaitMatching(timeout = 10.seconds) { … }` — suspends until the state matches; fails with
  the last state.
- `service<S>()` — the `@Urpc` service's generated client, calling the in-process server. For
  server behaviour no screen reaches.
- `given { … }` — runs scenarios, before a ViewModel opens or while it is open. A change made
  mid-test reaches an open ViewModel through the server's notifications, as in the app.

**Scenarios.** `suspend fun Scenario.<name>(…)` in the `:tests` module's `src/main`. Inside,
`server<T>()` and `client<T>()` resolve from the running graphs. A scenario writes through server
domain interfaces only and returns what it created (`Testing.Scenario.*`). Another feature's
`:tests` module depends on this one to reuse them.

**Edges.** A binding the project does not own (a third-party backend, the clock) is replaced per
test, loaded after the graph so it overrides:

```kotlin
subcutaneous(ukptTestGraph, edges = { server { single<Clock> { FakeClock } } }) { … }
```

A Fake in a `:tests` module implements only an IntegrationClient's interface, `kotlin.time.Clock`
or a `platform` interface (`Testing.Fake.subcutaneousEdgesOnly`). Ktor's `MockEngine` is for
backends the project does not own; the project's own server always runs for real.

**Coverage.** `./gradlew subcutaneousCoverage` writes the feature modules' coverage by Subcutaneous
Tests alone to `build/reports/kover/html/index.html` and `build/reports/kover/report.xml`. Only the
`:tests` modules' `subcutaneousTest` tasks are instrumented. It is advisory: there are no bounds.

**Memory.** Each test JVM starts one embedded Postgres; `ukpt.feature-tests` runs one test JVM at a
time with a 1 GB heap. The first test in a JVM takes about 3 s (Postgres start and migration), the
rest under 1 s.

## End-to-end tests

An End To End Test drives the desktop app's root composable, `AppContent()`, with
`runComposeUiTest`, against the server in-process. Write one per journey that crosses screens; the
behaviour inside a screen belongs in a Subcutaneous Test.

```kotlin
@Test
fun `a seeded greeting shows`() = inProcessServer(::serverDependencies) {
    given { greetingHistory("Hi there") }
    val rootContext = appRootContext()

    runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalRootContext provides rootContext) {
                KoinApplication(application = { modules(clientDependencies + clientEdges) }) {
                    AppContent()
                }
            }
        }
        awaitText("Hi there")
        onNodeWithText("Greet").performClick()
        awaitText("Hello again")
    }
}
```

- `inProcessServer(server, edges)` — from the harness. Starts the server graph against a new
  database and builds no client graph. `clientEdges` is the transport to that server plus the
  client edges; load it after `clientDependencies`. `given { }` runs scenarios from any `:tests`
  module the desktop test set depends on. Teardown stops the global Koin context that
  `KoinApplication` starts.
- `appRootContext()` — in the desktop test set. A root context on the app's navigation controller,
  for `LocalRootContext`; no `RootWindow` exists in `runComposeUiTest`. The controller is installed
  once per test JVM.
- `runComposeUiTest` is `androidx.compose.ui.test.v2.runComposeUiTest`. Waits are real time:
  `waitUntilExactlyOneExists(hasText(…), timeoutMillis)`. A dialog's button is
  `onNode(hasText("Reset") and hasAnyAncestor(isDialog()))`.
- The desktop test set depends on `:app:server` (for `serverDependencies`), the harness, the
  feature's `:tests` and `:api` modules, and `libs.compose.uiTest`.

Run them with `./gradlew endToEndTest`. The first test in a JVM takes about 5 s.

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
- `ClientUi.ViewModel.subcutaneouslyTested` — add a Subcutaneous Test that opens the ViewModel.
- `ClientUi.ViewModel.publicFunctionsExercised` — call the named function in a Subcutaneous Test of
  that ViewModel.
- `Testing.SubcutaneousTest.entryPointVocabularyOnly` — replace the repository or use case with the
  ViewModel or service that reaches it, and seed through a scenario.
- `Testing.SubcutaneousTest.viewModelsFromTheGraph` — open the ViewModel with `viewModel<VM, K>(key)`.
- `Testing.SubcutaneousTest.noInfrastructureImports` — move the binding to `edges`, the setup to a
  scenario, `runTest` to `awaitMatching`.
- `Testing.Scenario.seedsThroughServerDomain` — call a server domain interface instead of storage.
- `ModuleRules.testsModuleShape` — add the feature's `:client`/`:server` to the `:tests` build file.
- `Testing.EndToEndTest.drivesTheAppRoot` — run the test body in `inProcessServer(…)` and render
  `AppContent()` inside `runComposeUiTest`.
