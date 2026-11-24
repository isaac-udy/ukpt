---
name: ukpt-testing
description: >-
  Write, place, and run UKPT tests by category — Subcutaneous, Unit, Component,
  Snapshot, Wiring, Adapter, Check, End-to-end — with the category annotations
  from :platform:common:testing, the per-category Gradle tasks, the Subcutaneous
  harness (test graph, scenarios, edges, coverage), and the Testing
  architecture rules that check a test behaves as its category claims, and which
  tests to run while iterating. Use when adding or changing a test, choosing what
  kind of test to write, choosing which tests to run after a change, deciding
  whether to keep an end-to-end test, running one category, or fixing a
  Testing.* rule failure.
---

# ukpt-testing

Every test class has exactly one annotation from `platform.testing`. The category says what
the test may use besides the code under test; the `Testing` rules check it
(`platform/common/architecture/docs/testing.md`).

## Choosing a category

| Write a… | When | Where |
| --- | --- | --- |
| `@SubcutaneousTest` | Behaviour a user triggers through a screen, or a `@Urpc` service no screen calls. **The default.** | `:feature:<name>:tests`, `src/commonTest/` |
| `@UnitTest` | Logic with many inputs: parsing, validation, formatting, a branching UseCase | the module's `commonTest` (or a platform test set) |
| `@ComponentTest` | A `[Name]ScreenContent` with fixed state, driven through its robot; a Compose HTML `[Name]HtmlScreenContent`; or a shared primitive, navigation container or app-shell composable | the `:client` module's `src/jvmTest/` (Compose UI) or `src/jsTest/` (HTML); shared UI in its own `:platform:client:*` or `:app:client:common` module |
| `@SnapshotTest` | The module's one `PreviewSnapshotTest` | `src/androidHostTest/` |
| `@WiringTest` | An app shell's Koin graph (`verify()`) | the `:app` module's test set |
| `@AdapterTest` | One adapter against real infrastructure that a Subcutaneous Test covers poorly: a server adapter, or a client's device files, sensors or renderers | a server module's `src/test/`; a client module's platform test set (`jvmTest`, `iosSimulatorArm64Test`, …); `androidHostTest` for an adapter in `androidMain`, which `snapshotTest` runs |
| `@CheckTest` | Two committed files that must agree (CSS tokens, doc images) | a JVM test set |
| `@EndToEndTest` | A journey through the desktop app's UI | `:app:client:desktop`, `src/test/` |

Each Construct's documentation page lists the categories expected to cover it under "Tested by"
(`testedBy` in the catalog): a ViewModel is tested by a Subcutaneous or Unit Test, a Screen by a
Snapshot or Component Test.

Ask first whether a Subcutaneous Test covers the behaviour. A Unit Test constructs one production
class (`Testing.UnitTest.oneProductionClass`) and imports no DI, network, database, file or UI-test
API (`Testing.UnitTest.noInfrastructure`); collaborators are lambdas for `fun interface`s or
`Fake[Name]` classes. Constructing domain models, states and other value types (a `data class`, a
`value class`, or a class of constructor `val`s only) is test data and does not count.

## Writing one

Copy the nearest test of the same category:

- Subcutaneous: `feature/core/tests/src/commonTest/kotlin/feature/ukpt/tests/UkptViewModelTest.kt`
  (ViewModel entry), `GreetingServiceTest.kt` beside it (server entry)
- Unit: `feature/core/client/src/commonTest/kotlin/feature/ukpt/client/ui/AsyncStateLoadTest.kt`
- Component: `feature/core/client/src/jvmTest/kotlin/feature/ukpt/client/ui/UkptScreenContentTest.kt`
  (Compose UI), `feature/core/client/src/jsTest/kotlin/feature/ukpt/client/ui/UkptHtmlScreenContentTest.kt`
  (HTML)
- Robot: `feature/core/tests/src/commonMain/kotlin/feature/ukpt/tests/UkptScreenRobot.kt`
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
`expect*` or `await*` call, or a robot's `see*` call, directly or through a helper declared in the
same file. Every other top-level declaration in test sources is a `Fake[Name]` class or object, a
robot, a top-level fixture function or property, or a `@Composable` snapshot surface in
`androidHostTest`.

## Robots

Compose UI Component Tests and End To End Tests find and act on nodes only through robots
(`Testing.ComponentTest.drivesThroughRobots`, `Testing.EndToEndTest.drivesThroughRobots`). A
robot covers one screen and lives in the feature's `:tests` module, `src/commonMain`, from
`:platform:common:robots` (package `platform.testing.robots`).

1. **Tags.** Beside the screen, in `client.ui`, declare `object [Screen]Tags` with `const val`
   tags. Apply one to the screen's root and one to each node a robot uses:
   `Modifier.testTag(UkptScreenTags.GreetButton)`.
2. **Robot.** One class per screen, with its entry point in the same file:

   ```kotlin
   fun User.onUkptScreen(block: UkptScreenRobot.() -> Unit) =
       on(UkptDestination::class, UkptScreenTags.Screen, ::UkptScreenRobot, block)

   class UkptScreenRobot internal constructor(context: RobotContext) : ScreenRobot(context) {
       fun clickGreetButton() = click(UkptScreenTags.GreetButton)
       fun seeGreeting(text: String) = seeText(UkptScreenTags.LatestGreeting, text)
   }
   ```

   Name each function for what a user does or sees: `click…`, `enter…`, `clear…`, `select…`,
   `toggle…`, `scroll…`, `see…` (`Testing.Robot.userActionNames`). A robot uses one `[Screen]Tags`
   object (`Testing.Robot.oneScreen`). A screen in a shared UI module, such as a confirmation
   sheet every feature opens, keeps an `object [Name]Tags` of `const val`s beside it in that
   module; its robot uses that object.

   `ScreenRobot`'s steps: `click`, `see`, `seeNo`, `seeText`, `seeTextPart`, `seeEnabled`,
   `seeDisabled`, `enterText`, `clearText`, `scrollTo`, `clickCentre`, `seeContentDescription`,
   `seeProgress`, `seeSizeAtLeast`, `boundsOf`. For a node a design primitive draws without a tag
   of its own, the `…Within(tag, text)` and `…Described(tag, description)` steps find it inside a
   tagged ancestor. Add a `ScreenRobot` helper when a robot needs a new kind of step. A step
   with no common Compose UI test API calls an `expect fun` in `src/commonMain`, with an
   `actual` in the source set of each target the `:tests` module builds (`jvmMain`,
   `androidMain`, …).
3. **Use.** One `on[Screen] { }` block per screen visit, inside `asUser { }`:

   ```kotlin
   asUser(rootContext) {
       onUkptScreen { clickResetButton() }
       onConfirmResetDialog { clickResetButton() }
       onUkptScreen { seeNoGreetings() }
   }
   ```

   `on[Screen]` waits for Compose to go idle and for the screen's root tag. Given the app's
   `RootContext` (End To End Tests), it also waits until the screen's destination is Enro's active
   destination, and fails naming the active one. A Component Test calls `asUser { }` without it.
   Each step waits up to 10 s; a `see…` failure names the tag, the expected text and the text
   shown.

A `:client` module's `jvmTest` depends on its own `:tests` module for the robots, and on
`compose.desktop.currentOs`.

## Subcutaneous tests

The harness is `:platform:common:subcutaneous-server` (package `platform.testing.subcutaneous`),
which runs the server in-process, over `:platform:common:subcutaneous`, which builds the client
graph. A feature's `:feature:<name>:tests` module applies `ukpt.feature-tests` and depends on the
harness and the feature's `:client` and `:server` in `commonMain` (`ukpt-feature-slice` scaffolds
it). The module's `src/commonMain` contains the test graph, the scenarios and the robots, and
`src/commonTest` the tests. A project without a server of its own depends on
`:platform:common:subcutaneous` alone: see "Without a server" below.

**Hosts.** The `ukpt.subcutaneousHosts` property in `gradle.properties` lists the hosts that run
the tests (default `jvm,android`). A `gradle.properties` in a `:tests` module's directory overrides
the list for that module. Each host has its task:

| Host | Task | Runs |
| --- | --- | --- |
| `jvm` | `subcutaneousJvmTest` | every graph |
| `android` | `subcutaneousAndroidHostTest` | every graph; a JVM with the Android variants of every dependency |
| `ios` | `subcutaneousIosSimulatorArm64Test` | graphs without a server; macOS only |
| `wasmJs` | `subcutaneousWasmJsTest` | graphs without a server; headless Chrome |
| `js` | `subcutaneousJsTest` | graphs without a server; headless Chrome |

The `ios`, `wasmJs` and `js` tasks run the base test task, so a `--tests` filter goes on that task:
`:feature:<name>:tests:jsBrowserTest --tests '*<Name>ViewModelTest'`. A project without a desktop
client sets `ukpt.subcutaneousHosts=android`. Listing a host makes its task available; CI may run
one listed host only, such as `subcutaneousJvmTest` when the list contains `jvm`. The other listed
hosts run on demand, such as `subcutaneousAndroidHostTest` after a change to an Android binding.
The in-process server and embedded Postgres run on the JVM, so a `:tests` module that depends on a
`:server` fails configuration when its list contains `ios`, `wasmJs` or `js`.
A `:tests` module holds only Subcutaneous Tests (`Testing.SubcutaneousTest.testsModuleHoldsOnlySubcutaneousTests`),
and each test function is `= subcutaneous(graph) { … }` (`.runsInTheHarness`).

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
thread (the event loop on JS and wasm); waits are real time. `subcutaneous` returns a `TestResult`,
which on JS and wasm is the Promise the test runner waits for, so each test is an expression body.

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
  `key` (`screen.navigation`; use the enro-test assertions on it). `viewModel<VM, K>(key) { set(…) }`
  sets navigation metadata on the key's instance before the ViewModel opens.
- `screen.navigation.awaitOpened(key)`, `awaitOpened<K> { … }` and `awaitClosed()` — wait for an
  open or a close the ViewModel performs later, such as after a result is received. `awaitOpened<K>()`
  checks the key's type; enro-test's `assertOpened<K>()` alone matches any open.
- `state.awaitMatching(timeout = 10.seconds) { … }` — suspends until the state matches; fails with
  the last state.
- `awaitAsserted(timeout = 10.seconds) { … }` — retries an assertion until it passes; fails with
  its last failure. For navigation a ViewModel performs after a state change:
  `awaitAsserted { screen.navigation.assertClosed() }`.
- `service<S>()` — the `@Urpc` service's generated client, calling the in-process server. For
  server behaviour no screen calls.
- `given { … }` — runs scenarios, before a ViewModel opens or while it is open. An open ViewModel
  receives a change made mid-test through the server's notifications, as in the app.

**Scenarios.** `suspend fun Scenario.<name>(…)` in the `:tests` module's `src/commonMain`. Inside,
`server<T>()` and `client<T>()` resolve from the running graphs. A scenario writes through server
domain interfaces only and returns what it created (`Testing.Scenario.*`). Another feature's
`:tests` module depends on this one to reuse them.

**Inspections.** A test checks the other end of the system, besides the ViewModel state, through
Inspections in the `:tests` module's `src/commonMain`: `suspend fun ServerInspection.<name>()`
reads the server, `fun ClientInspection.<name>()` reads the client graph. A test calls them inside
`inspectServer { }` or `inspectClient { }`, which retry the block until the block passes or
10 s of real time pass, and return the block's result:

```kotlin
// src/commonMain
suspend fun ServerInspection.currentGreetingHistory(): List<Greeting> = server<FlowOfGreetingHistory>()().first()

suspend fun ServerInspection.storedGreetingTexts(): List<String> = database {
    GreetingsTable.selectAll().map { it[GreetingsTable.text] }
}

// src/commonTest
screen.viewModel.onGreetClicked()
screen.viewModel.state.awaitMatching { it.greetingTexts() == listOf("Hello") }
inspectServer { assertEquals(listOf("Hello"), storedGreetingTexts()) }
```

- `server<T>()` resolves a server domain interface named for a read (`Get…`, `FlowOf…`, `List…`,
  `Find…`, …) or a Fake bound in `edges = { server { … } }`.
- `database { }` (`:platform:common:subcutaneous-server`) runs a read-only transaction on the
  test's database, for a stored fact the server domain does not expose. A read through `database { }`
  depends on the schema; prefer a domain read when the domain exposes the fact. The generated
  tables are in `:platform:server:postgres`, which the `:tests` module declares in `commonMain`.
- `client<T>()` resolves a client domain read interface or a Fake bound in `edges = { client { … } }`.
- A Fake records its calls in a `Recording<T>`: the Fake calls `record(call)`, and an Inspection
  reads `calls`, `assertSingle { … }` or `assertNone { … }`:

```kotlin
class FakePaymentGateway : PaymentGateway {
    val requests = Recording<PaymentRequest>()
    override suspend fun charge(request: PaymentRequest): Receipt { requests.record(request); return Receipt("r-1") }
}

fun ServerInspection.paymentRequests(): Recording<PaymentRequest> = server<FakePaymentGateway>().requests

inspectServer { paymentRequests().assertSingle { it.amount == 10.00 } }
```

An Inspection only reads (`Testing.ServerInspection.readsOnly`, `Testing.ClientInspection.readsOnly`):
no domain interface without a read name, no Exposed write, no SQL, no data or UI class.
`inspectServer { }` needs a graph with a server and fails without one.

**Edges.** A binding the project does not own (a third-party backend, the clock) is replaced per
test, loaded after the graph so it overrides:

```kotlin
subcutaneous(ukptTestGraph, edges = { server { single<Clock> { FakeClock } } }) { … }
```

A Fake in a `:tests` module implements only an IntegrationClient's interface, `kotlin.time.Clock`
or a `platform` interface (`Testing.Fake.subcutaneousEdgesOnly`). Ktor's `MockEngine` is for
backends the project does not own; the project's own server always runs for real.

Client file storage is an edge. Without one, Subcutaneous Tests write into the developer's real
app data. Put the storage root in a value type in a `:platform:client:*` module, bind it in the
shell, and override it in an edge with a per-test directory:

```kotlin
fun Edges.temporaryAppFiles() {
    client {
        single { AppFilesDirectory(createTempDirectory("subcutaneous").toString()) } onClose { directory ->
            directory?.let { File(it.path).deleteRecursively() }
        }
    }
}
```

**Host edges.** A binding that one host cannot build for any test, such as a database driver or a
DataStore that needs an Android `Context` on the Android host, is replaced in the graph's
`hostEdges` (`Testing.HostEdges`). The harness loads host edges after the graph and before a
test's `edges`:

```kotlin
// src/commonMain
expect val notesHostEdges: Edges.() -> Unit

val notesTestGraph = SubcutaneousGraph(
    client = featureClient(notesClientDependencies),
    server = featureServer(notesServerDependencies),
    hostEdges = notesHostEdges,
)

// src/androidMain
actual val notesHostEdges: Edges.() -> Unit = {
    client { single<SqlDriver> { JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY) } }
}

// src/jvmMain
actual val notesHostEdges: Edges.() -> Unit = {}
```

A graph whose bindings every host can build declares no host edges.

The Android host runs without Robolectric: `android.jar` there is stubs, so a production binding
that calls an Android API or needs a `Context` fails on `subcutaneousAndroidHostTest`. Bind each
such dependency through a small factory or `fun interface` in a `:platform:client:*` module (a
DataStore factory, an SQL driver, a storage root), so a host edge replaces that one binding and
the rest of the graph stays production code.

**Without a server.** A project whose backend it does not own (a third-party REST API) has no
`:server` modules and no `:platform:common:subcutaneous-server`. Its `:tests` module depends on
`:platform:common:subcutaneous` and the feature's `:client`, and runs on every host. The graph
lists client modules only; `featureClient` adds the network modules:

```kotlin
val projectsTestGraph = SubcutaneousGraph(
    client = featureClient(projectsClientDependencies),
)
```

The backend is an edge. A `Fake[Name]Backend` in `src/commonMain` stores the records a test seeds and
answers the client's requests through a Ktor `MockEngine`. An `Edges` extension beside it binds
the fake and the engine, so the test files import neither Ktor nor Koin
(`Testing.SubcutaneousTest.noInfrastructureImports`):

```kotlin
fun Edges.fakeBackend() {
    client {
        single { FakeProjectsBackend() }
        single<HttpClientEngine> { get<FakeProjectsBackend>().engine }
    }
}

suspend fun Scenario.projects(vararg names: String): List<Project> =
    client<FakeProjectsBackend>().addProjects(*names)
```

`subcutaneous(projectsTestGraph, edges = { fakeBackend() }) { … }` then runs the production client
graph against it. `server<T>()`, `service<S>()` and `edges = { server { … } }` need a graph with a
server and fail without one.

**Coverage.** `./gradlew testCoverageReport --no-configuration-cache` runs every category except
End-to-end with Kover and writes `build/reports/test-coverage/`: `index.html` (by Construct, by
feature, by module, gaps, and a page per source file with each line marked), `index.md` (the same
tables with `path:line` gaps) and `coverage.json`. A line is covered when a test from a category its
Construct's "Tested by" lists runs it, and incidental when only tests from other categories run it.
Lines that no Construct claims follow the module expectation in the root `testCoverage { }` block
(`testedBy(":platform:server:postgres", "adapter", "subcutaneous")` or
`noTests(":platform:common:robots", "test infrastructure")`). Constructs and modules that expect no
tests are not in the totals. `./gradlew renderTestCoverageReport` rewrites the report from the last
run without running the tests. Kover measures the JVM and the Android host only. The report is
advisory: there are no bounds.

**Memory.** Each test JVM with a server starts one embedded Postgres; `ukpt.feature-tests` runs one test JVM at a
time with a 1 GB heap. The first test in a JVM takes about 2–3 s (Postgres start and migration), the
rest about 0.05 s plus the test's own work: background threads clone the next tests' databases and
drop finished ones.

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
        asUser(rootContext) {
            onUkptScreen {
                seeGreeting("Hi there")
                clickGreetButton()
                seeGreeting("Hello again")
            }
        }
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
- `runComposeUiTest` is `androidx.compose.ui.test.v2.runComposeUiTest`. Pass `rootContext` to
  `asUser`, so each `on[Screen]` checks the active destination.
- The desktop test set depends on `:app:server` (for `serverDependencies`), the harness, the
  feature's `:tests` and `:api` modules, and `libs.compose.uiTest`.

Run them with `./gradlew endToEndTest`. The first test in a JVM takes about 5 s.

## Running a category

A task name given without a module path runs in every module that has it:

```
./gradlew unitTest
./gradlew subcutaneousJvmTest       # or the task of another host in ukpt.subcutaneousHosts
./gradlew componentTest     # also runs each :client module's jsTest
./gradlew wiringTest checkTest adapterTest endToEndTest
./gradlew snapshotTest --no-configuration-cache
```

One module: `./gradlew :feature:core:client:unitTest`. On the JVM and the Android host the category annotations apply
JUnit 5 tags and timeouts (10 s unit; 30 s component, wiring, check; 60 s adapter, subcutaneous;
120 s end-to-end). kotlin.test has no tags on JS, wasm or iOS, so those targets run through their
own tasks (`jsTest`, `wasmJsTest`, `iosSimulatorArm64Test`). `test`/`jvmTest` still run every
category.

## Development loop

Run the narrowest tests that cover the change while iterating, the feature's categories before
handing back, and the full sweep once, before a pull request. CI runs the full sweep on every pull
request (`ukpt-verify`, "CI"). Run builds one at a time.

**After each edit:**

- The test class for the code that changed, with a filter:
  `./gradlew :feature:<name>:tests:subcutaneousJvmTest --tests '*<Name>ViewModelTest'`. Gradle
  compiles only what that test needs.
- `./gradlew verifyArchitecture` after adding, moving or renaming a declaration.
- Not `test`, `jvmTest`, `allTests`, `check` or a category task without a module path: each runs
  every module.

**Before handing back**, the categories of the modules that changed:

| Changed | Run |
| --- | --- |
| A ViewModel, its State, or what it calls in `client.domain`/`client.data` | `:feature:<name>:tests:subcutaneousJvmTest` (the first host in `ukpt.subcutaneousHosts`) |
| A `@Urpc` service, `server.domain` or `server.data` | `:feature:<name>:tests:subcutaneousJvmTest`; `adapterTest` in the module whose storage changed |
| A `[Name]ScreenContent`, its tags, or a robot | `:feature:<name>:client:componentTest` and `:feature:<name>:client:snapshotTest --no-configuration-cache` |
| A Compose HTML screen | `:feature:<name>:client:jsTest` |
| Logic with a Unit Test | that module's `unitTest` |
| A Koin module or a constructor | `wiringTest` |
| Design tokens or `ukpt-design.css` | `checkTest` and `:platform:client:design:snapshotTest --no-configuration-cache` |
| Navigation between screens, `App.kt`, or a screen in a journey | `:app:client:desktop:endToEndTest` |
| `commonMain` code that iOS compiles | `:app:client:common:compileKotlinIosSimulatorArm64` |

Then `verifyArchitecture` and `validateTemplate`.

**Snapshots.** Verify only the modules whose previews changed. Record
(`recordPaparazzi --no-configuration-cache`) only after an intended visual change, in that module,
and open the changed golden images before committing them. The snapshot test JVM runs in locale
`en-US` and time zone `UTC` on every machine; the Gradle properties `ukpt.snapshotLocale` and
`ukpt.snapshotTimeZone` in `gradle.properties` change the pair.

**Timings** on a 16 GB M1 Pro, with the Gradle daemon running and the code compiled, the test task
forced to run (`--rerun`), in seconds:

| Run | s |
| --- | --- |
| `validateTemplate` | 3 |
| `verifyArchitecture` | 15 |
| `:feature:core:tests:subcutaneousJvmTest`, one class or all 11 tests | 14–18 |
| `:feature:core:tests:subcutaneousAndroidHostTest`, all 11 tests | 17 |
| `:feature:core:client:componentTest -x jsTest` | 7 |
| `:feature:core:client:jsTest` | 5 |
| `:feature:core:client:unitTest` | 5 |
| `:feature:core:client:snapshotTest --no-configuration-cache` | 13–32 |
| `:app:client:desktop:endToEndTest` | 10 |
| `wiringTest`, every module | 9 |
| every category except snapshots, every module, one invocation | 30 |
| recompiling `:feature:core:client` for the JVM | 5 |
| recompiling `:app:client:common` for the iOS simulator | 13 |

Most of a Subcutaneous run is starting the test JVM and Postgres, so a `--tests` filter saves time
only in a module with many tests. A snapshot run that configures without the configuration cache
takes the longer time. A long sweep comes from compiling the changed modules and their dependents
for every target, and from separate invocations: name several tasks in one `./gradlew` call
rather than running them one after another.

### Checking a feature: by hand, a throwaway test, or a kept test

- **By hand** (`ukpt-drive-app`): layout, spacing, how a screen looks and responds. No task re-runs
  the manual check, so the manual check is not a test of behaviour.
- **A throwaway End To End Test:** a journey across several screens that would take many steps to
  repeat by hand after each change. Write it in `:app:client:desktop`'s `src/test` with the
  feature's robots, run it while iterating, and delete it before committing unless it meets the
  next point.
- **A kept End To End Test:** one per main journey of the app, for what a Subcutaneous Test can't
  cover: screens bound to their ViewModels, navigation between screens, the shell starting. A
  behaviour inside one screen, even one an End To End Test found, gets a Subcutaneous Test.

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
  ViewModel or service that calls that repository or use case, and seed through a scenario.
- `Testing.SubcutaneousTest.viewModelsFromTheGraph` — open the ViewModel with `viewModel<VM, K>(key)`.
- `Testing.SubcutaneousTest.noInfrastructureImports` — move the binding to `edges`, the setup to a
  scenario, `runTest` to `awaitMatching`.
- `Testing.Scenario.seedsThroughServerDomain` — call a server domain interface instead of storage.
- `Testing.ServerInspection.readsOnly`, `Testing.ClientInspection.readsOnly` — read through a
  domain interface named for a read, `database { }` queries or a Fake's `Recording`; move a write
  to a Scenario.
- `Testing.ServerInspection.placement`, `Testing.ClientInspection.placement` — move the Inspection
  to the `:tests` module's `src/commonMain`.
- `ModuleRules.testsModuleShape` — add the feature's `:client`/`:server` to the `:tests` build file.
- `Testing.EndToEndTest.drivesTheAppRoot` — run the test body in `inProcessServer(…)` and render
  `AppContent()` inside `runComposeUiTest`.
- `Testing.ComponentTest.drivesThroughRobots`, `Testing.EndToEndTest.drivesThroughRobots` — move
  the node lookup into the screen's robot and call it from an `on[Screen] { }` block.
- `Testing.Robot.oneScreen` — move the steps for the second screen to that screen's robot.
- `Testing.Robot.userActionNames` — rename for the control and the action: `greet()` →
  `clickGreetButton()`.
- `Testing.Robot.hasEntryPoint` — add `fun User.on[Screen](block: [Screen]Robot.() -> Unit)`.
