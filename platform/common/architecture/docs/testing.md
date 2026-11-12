> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/testing/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Testing](../src/main/kotlin/architecture/rules/testing/Testing.kt)

_This RuleGroup governs test sources._

Every test class declares one category with an annotation from `:platform:common:testing`. The
category states what the test may use besides the code under test, which decides how long a
run takes and what a failure means.

| Category | Exercises | Uses for real | Runs on |
| --- | --- | --- | --- |
| `@SubcutaneousTest` | a ViewModel, or a `@Urpc` service, from the production DI graph | everything below it: the server in-process, an embedded database | JVM |
| `@UnitTest` | one production class over many inputs | nothing; collaborators are test fakes or lambdas | every target |
| `@ComponentTest` | one screen's content, with fixed state | the renderer | JS (DOM), JVM |
| `@SnapshotTest` | previews, compared with golden images | the renderer | Android host |
| `@WiringTest` | a DI graph | the DI container | JVM |
| `@AdapterTest` | one infrastructure adapter | the infrastructure | JVM |
| `@CheckTest` | two committed artifacts | files | JVM |
| `@EndToEndTest` | an application shell, through its UI | the whole application | Desktop JVM |

Subcutaneous tests are the main suite: most behaviour is covered by driving a ViewModel and
reading its state. A unit test is for logic with many inputs. A test that needs another kind
of coverage states it with its category.

On the JVM each annotation declares a JUnit 5 tag and timeout. `./gradlew unitTest` runs the
unit tests of every module; each category has its task (`subcutaneousTest`, `componentTest`,
`wiringTest`, `adapterTest`, `checkTest`, `endToEndTest`, and `snapshotTest`, which needs
`--no-configuration-cache`). kotlin.test has no tags on JS, wasm or iOS, so `componentTest`
also runs the module's `jsTest`.

Component Tests and End To End Tests drive Compose UI screens through [Robots](#robot), one
per screen, named for what the user does: `clickGreetButton()`, `seeGreeting("Hi there")`.

Test-support declarations (fakes, fixtures, snapshot surfaces, scenarios, test graphs, robots)
are Constructs too: every top-level declaration in test sources matches exactly one.

##### Constructs

* [Subcutaneous Test](#subcutaneous-test)
* [Unit Test](#unit-test)
* [Component Test](#component-test)
* [Snapshot Test](#snapshot-test)
* [Wiring Test](#wiring-test)
* [Adapter Test](#adapter-test)
* [Check Test](#check-test)
* [End To End Test](#end-to-end-test)
* [Fake](#fake)
* [Snapshot Surface](#snapshot-surface)
* [Scenario](#scenario)
* [Test Graph](#test-graph)
* [Robot](#robot)
* [Fixture](#fixture)

##### Rules

* Every test class (a class declaring a `@Test` function or extending `PreviewSnapshotTestCase`) must have exactly one test category annotation
    * **Why:** The category decides which task runs the test and which rules check it.
* Every `@Test` function must assert: call an `assert*`, `fail`, `verify`, `expect*` or `await*` function, or a Robot's `see*` function, directly or through a function declared in the same file
    * **Why:** A test without an assertion passes whatever the code does. It reports coverage that does not exist.
    * **Note:** Detection is by name in the source text, so an assertion helper declared in another file needs a name that matches.

---

## [Subcutaneous Test](../src/main/kotlin/architecture/rules/testing/SubcutaneousTest.kt)

A `@SubcutaneousTest` class: drives a real entry point (a ViewModel, or a `@Urpc` service for
server behaviour that no screen uses) built from the production DI graph, with the real
server running in-process against an embedded database. It asserts on what the entry point
exposes: ViewModel state, navigation results, service responses.

Subcutaneous tests are the main suite. Subcutaneous tests exercise the code a user runs through
a screen, and do not change when the domain and data layers below the ViewModel are restructured.

```kotlin
@SubcutaneousTest
class UkptViewModelTest {
    @Test
    fun `a greeting added while the screen is open appears`() = subcutaneous(ukptTestGraph) {
        val screen = viewModel<UkptViewModel, UkptDestination>(UkptDestination)
        screen.viewModel.state.awaitMatching { it.greetingTexts() == emptyList<String>() }

        given { greetingHistory("Kia ora") }

        screen.viewModel.state.awaitMatching { it.greetingTexts() == listOf("Kia ora") }
    }
}
```

`subcutaneous(graph)` gives each test a new database cloned from the migrated template, runs
the [Test Graph](#test-graph)'s server modules in-process on Ktor's test engine, and builds
the client graph against it. `viewModel<VM, K>(key)` opens a ViewModel from the client graph
with an enro-test navigation handle; `service<S>()` calls a `@Urpc` service through the client
transport. `given { }` runs [Scenarios](#scenario), before or after a ViewModel opens.
`awaitMatching` waits up to 10 seconds of real time for the state to match.

##### Requirements

* A Subcutaneous Test is a class
* A Subcutaneous Test is annotated `@SubcutaneousTest`

##### Rules

* A Subcutaneous Test must live in the `src/test` source set of a `:feature:[name]:tests` module
    * **Why:** The `:tests` module depends on the feature's `:client` and `:server`, which no other module may combine.
* A Subcutaneous Test must reference only entry-point vocabulary from production code: ViewModels, their states, Destinations, UI value types, client domain models and their extensions, domain exceptions and constants, shared wire types, and `@Urpc` service interfaces
    * **Why:** The test drives the entry point and reads what it exposes. A reference to a repository, a use case or a storage class fixes that class in place, and the test changes when the layers below the entry point are restructured.
    * **Note:** Test data comes from Scenarios (`given { }`), which may use the server domain.
* A Subcutaneous Test must not construct a ViewModel; it opens one from the client graph with `viewModel<[Name]ViewModel, [Name]Destination>(key)`
    * **Why:** A ViewModel built by hand takes collaborators the test chose, not the ones the application binds.
* Every `@Test` function of a Subcutaneous Test must run its body in `subcutaneous(graph) { … }`
    * **Why:** The harness starts the server and the client graph against a new database, and tears them down after the test.
* A Subcutaneous Test must not import Koin, Ktor, Exposed, JDBC or `kotlinx.coroutines.test`
    * **Why:** The harness owns the graph, the server, the database and the dispatchers. Virtual time from `kotlinx.coroutines.test` does not advance real I/O; `awaitMatching` waits in real time.

---

## [Unit Test](../src/main/kotlin/architecture/rules/testing/UnitTest.kt)

A `@UnitTest` class: one production class exercised over many inputs, with every collaborator
a fake declared in test sources or a lambda.

A unit test is for logic whose input space is wider than the screens that call the logic: parsers,
validators, formatters, reducers, a UseCase with many branches. Behaviour that a user runs
through a screen is covered by a [Subcutaneous Test](#subcutaneous-test) instead, which does
not change when the layers between the ViewModel and the database are restructured.

##### Requirements

* An Unit Test is a class
* An Unit Test is annotated `@UnitTest`

##### Rules

* A Unit Test must live in a test source set other than `androidHostTest`, outside a `:feature:[name]:tests` module
    * **Why:** `androidHostTest` runs Paparazzi; a `:tests` module contains Subcutaneous Tests.
* A Unit Test must not import dependency injection, network, database, file-system, or UI-test APIs
    * **Why:** Those APIs are what the other categories use. A unit test that imports those APIs is a Wiring, Adapter, Subcutaneous or Component test, and is annotated as one.
    * **Note:** `io.ktor.http.HttpStatusCode` is a value, not infrastructure, and may be imported.
* A Unit Test must construct at most one production class; constructing domain models, states and other value types is test data
    * **Why:** A test that assembles several production classes is testing their combination, which is what a Subcutaneous Test covers with the real dependency graph.
    * **Note:** Constructed means the class name followed by `(` in the test file's code.
    * **Note:** A value type wherever it lives — a `data class`, a `value class`, an `enum class`, a navigation key, a class whose only members are `val`s declared in its primary constructor, or a class with no supertype and no constructor parameters — is test data.

---

## [Component Test](../src/main/kotlin/architecture/rules/testing/ComponentTest.kt)

A `@ComponentTest` class: renders one screen's `[Name]ScreenContent` with fixed state, then
reads and drives the rendered output through the screen's [Robot](#robot).

```kotlin
@Test
fun `a summary shows the latest greeting`() = runComposeUiTest {
    setContent { UkptTheme { UkptScreenContent(state = summaryState, onGreet = {}, onRetry = {}, onReset = {}) } }
    asUser {
        onUkptScreen { seeGreeting("Hi there") }
    }
}
```

The Compose HTML screens are tested in `src/jsTest/` with `html-test-utils`, which reads the
DOM; robots drive Compose UI only.

Shared UI — the design primitives, the navigation containers and the app shell — is tested the
same way in its own `:platform:client:*` or `:app:client:common` module, rendering the
primitive directly: there is no screen, so there is no robot.

##### Requirements

* A Component Test is a class
* A Component Test is annotated `@ComponentTest`

##### Rules

* A Component Test must live in the `jsTest`, `jvmTest` or `desktopTest` source set of a `:client`, `:platform:client:*` or `:app:client:common` module
    * **Why:** `[Name]ScreenContent` is `internal` to its `:client` module, and a shared primitive's internals to its own.
* A feature's Component Test must reference only composables, ViewModel states, and the value types those composables and states use from production code, and must not import dependency injection
    * **Why:** The test fixes the state and reads what renders. A ViewModel, a repository, or the DI graph in the test makes it a Subcutaneous Test.
    * **Note:** Shared UI tests are not checked: a navigation container or the app shell is tested with the destinations it hosts.
* A feature's Compose UI Component Test must find and act on nodes only through Robots: no `onNode…`, `onAllNodes…`, `onRoot`, `perform…` or node `waitUntil…` calls
    * **Why:** The robot owns the screen's tags, so the Component Test checks the same steps the End To End Tests take.
    * **Note:** A Component Test in `jsTest` renders Compose HTML and is not checked, nor is a shared UI test, which has no screen to give a robot.

---

## [Snapshot Test](../src/main/kotlin/architecture/rules/testing/SnapshotTest.kt)

A `@SnapshotTest` class: renders previews with Paparazzi and compares each with its committed
golden image. A module's `PreviewSnapshotTest` extends `PreviewSnapshotTestCase` from
`dev.isaacudy.udytils:snapshot`, which renders every `@Preview` in the package tree it scans.

```
./gradlew :feature:core:client:recordPaparazzi --no-configuration-cache
./gradlew snapshotTest --no-configuration-cache
```

##### Requirements

* A Snapshot Test is a class
* A Snapshot Test is annotated `@SnapshotTest`

##### Rules

* A Snapshot Test must live in the `androidHostTest` source set
    * **Why:** Paparazzi runs as an Android host test.
* A Snapshot Test must extend `PreviewSnapshotTestCase` or use `SnapshotRule`
    * **Why:** The harness owns the golden layout and the device and rendering defaults.

---

## [Wiring Test](../src/main/kotlin/architecture/rules/testing/WiringTest.kt)

A `@WiringTest` class: resolves an application shell's DI graph with Koin's `verify()`, which
checks that every registered constructor's parameters have a binding. Each shell keeps its
module list in `ClientDependencies.kt` or `ServerDependencies.kt` so one test covers it.

##### Requirements

* A Wiring Test is a class
* A Wiring Test is annotated `@WiringTest`

##### Rules

* A Wiring Test must live in an `:app` module's test source set
    * **Why:** The `:app` shells assemble the graphs.
* A Wiring Test must call Koin's `verify()`
    * **Why:** `verify()` resolves every binding without starting the application.

---

## [Adapter Test](../src/main/kotlin/architecture/rules/testing/AdapterTest.kt)

An `@AdapterTest` class: one infrastructure adapter checked against the real infrastructure,
for behaviour a Subcutaneous Test covers poorly: transaction semantics, concurrency, a query's
edge cases. `TransactionRunnerTest` runs against an embedded Postgres.

A client's adapters are the device's files, audio, sensors and document renderers, checked from
a platform test source set (`jvmTest`, `iosSimulatorArm64Test`, `androidHostTest`, …) of the
module that owns them. An Android adapter in `androidMain` is checked from `androidHostTest`,
the only host test set that compiles against it; it runs with that source set's test task, so
`snapshotTest` runs it, not `adapterTest`.

##### Requirements

* An Adapter Test is a class
* An Adapter Test is annotated `@AdapterTest`

##### Rules

* An Adapter Test must live in the `src/test` source set of a `:platform:server:*` or `:feature:[name]:server` module, of `:app:server` when it spans more than one feature, or a platform test source set of a `:platform:client:*` or `:feature:[name]:client` module
    * **Why:** Server adapters run on the JVM; a client adapter runs only on the platform it adapts, so its test can't live in `commonTest`. A feature `:server` may not depend on another, so a test of two features' adapters together lives in the server shell that composes them.

##### Guidance

* Prefer a Subcutaneous Test; an Adapter Test is for behaviour a Subcutaneous Test cannot cover
    * **Why:** Each Adapter Test fixes an adapter's interface in place, so restructuring the adapter changes the test.
    * **Audited:** a test reports non-conforming code without ever failing.

---

## [Check Test](../src/main/kotlin/architecture/rules/testing/CheckTest.kt)

A `@CheckTest` class: checks that two committed artifacts agree, such as the Compose HTML
stylesheet's tokens and the Compose design tokens (`UkptDesignCssTest`), or the design-system
pages and the goldens they embed (`DesignSystemDocImagesTest`). It tests the project, not
application behaviour.

##### Requirements

* A Check Test is a class
* A Check Test is annotated `@CheckTest`

##### Rules

* A Check Test must live in a JVM test source set: `jvmTest`, `desktopTest` or `src/test`
    * **Why:** A check reads files from disk, which the JVM test tasks can do.

---

## [End To End Test](../src/main/kotlin/architecture/rules/testing/EndToEndTest.kt)

An `@EndToEndTest` class: drives the desktop application's root composable through its UI with
Compose's `runComposeUiTest`, against the server running in-process. It covers what a
Subcutaneous Test does not: that screens bind to their ViewModels and that the shell starts.

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
            }
        }
    }
}
```

`inProcessServer(server)` starts the server graph on Ktor's test engine over a new database and
builds no client graph; `clientEdges` points the application's own graph at it. Teardown stops
the Koin context `KoinApplication` started. Each step goes through a screen's
[Robot](#robot), in one `on[Screen] { }` block per screen visit.

##### Requirements

* An End To End Test is a class
* An End To End Test is annotated `@EndToEndTest`

##### Rules

* An End To End Test must live in `:app:client:desktop`'s `src/test` source set
    * **Why:** The desktop shell is the one application that runs the UI and the server in one JVM.
* Every `@Test` function of an End To End Test must call `inProcessServer(`, `runComposeUiTest` and `AppContent(`
    * **Why:** The test covers the application as it starts: its root composable and its own client graph, against the real server. A test that renders one screen, or builds the client graph itself, is a Component or Subcutaneous Test.
* An End To End Test must find and act on nodes only through Robots: no `onNode…`, `onAllNodes…`, `onRoot`, `perform…` or node `waitUntil…` calls
    * **Why:** A journey reads as one block of user steps per screen, and a screen's tags change in one place, its robot.

---

## [Fake](../src/main/kotlin/architecture/rules/testing/Fake.kt)

A class or object in test sources named `Fake[Name]`: a hand-written stand-in for a production
interface. A `fun interface` collaborator is faked with a lambda instead, which needs no
declaration.

##### Requirements

* A Fake satisfies one of: {is a class, is an object}
* A Fake is named `Fake[Name]`
* A Fake declares no `@Test` function

##### Rules

* In a `:feature:[name]:tests` module, a Fake must implement only an interface an IntegrationClient provides, `kotlin.time.Clock`, or an interface declared under `platform`
    * **Why:** A Subcutaneous Test runs the production graph. A fake replaces a binding only at the system's edges: a backend the project does not own, the clock, a platform service. It is bound in `subcutaneous(graph, edges = { server { … } })`.

---

## [Snapshot Surface](../src/main/kotlin/architecture/rules/testing/SnapshotSurface.kt)

A `@Composable` function in `androidHostTest`: a preview that exists only to be snapshotted,
such as a design-system doc surface, and the frame it renders in.

##### Requirements

* A Snapshot Surface is a function
* A Snapshot Surface is annotated `@Composable`
* A Snapshot Surface lives in `androidHostTest`

---

## [Scenario](../src/main/kotlin/architecture/rules/testing/Scenario.kt)

A function with a `Scenario` receiver, in a `:feature:[name]:tests` module's `src/main`: puts
the test database into a state through the feature's server domain interfaces. A test calls it
inside `given { }`, before it opens a ViewModel or while one is open; it returns what it created.

```kotlin
suspend fun Scenario.greetingHistory(vararg texts: String): List<Greeting> {
    val updateGreetingHistory = server<UpdateGreetingHistory>()
    texts.forEach { updateGreetingHistory.add(it) }
    return server<FlowOfGreetingHistory>()().first()
}
```

Another feature's `:tests` module depends on this one to reuse its scenarios.

##### Requirements

* A Scenario has a `Scenario` receiver

##### Rules

* A Scenario must write through server domain interfaces: no server data classes, generated tables, Exposed, JDBC or SQL
    * **Why:** A row written past the domain skips the invariants the server enforces, and the test then covers a state the application cannot produce. A Scenario that calls the domain also stays valid when the storage below it changes.

##### Guidance

* A Scenario should return what it created, such as the seeded domain models
    * **Why:** The test refers to seeded records by the returned values instead of reading them back through the screen.
    * **Audited:** a test reports non-conforming code without ever failing.

---

## [Test Graph](../src/main/kotlin/architecture/rules/testing/TestGraph.kt)

A top-level `SubcutaneousGraph` in a `:feature:[name]:tests` module's `src/main`: the production
Koin modules the feature's Subcutaneous Tests run, `[name]TestGraph`.

```kotlin
val ukptTestGraph = SubcutaneousGraph(
    client = featureClient(ukptClientDependencies),
    server = featureServer(ukptServerDependencies),
)
```

`featureClient` adds the network modules and `featureServer` the Postgres modules. A test
replaces a binding at the system's edges (a backend the project does not own, a clock) with
`subcutaneous(graph, edges = { server { … } })`, not in the graph.

A project with no server of its own lists only client modules, including the `:platform`
module that builds its HTTP client, and fakes the backend at the client edge:

```kotlin
val projectsTestGraph = SubcutaneousGraph(
    client = listOf(networkDependencies, projectsClientDependencies),
)
```

##### Requirements

* A Test Graph is a top-level property initialised with `SubcutaneousGraph(`

##### Rules

* A `:feature:[name]:tests` module must declare exactly one Test Graph, and a Test Graph must live in one
    * **Why:** The graph repeats the module list the `:app` shells install for the feature. A second graph is a second list to keep in step with them.
* A Test Graph must list production Koin modules only: each feature's `[name]ClientDependencies` and `[name]ServerDependencies`, and `:platform` modules
    * **Why:** A binding declared in the graph replaces production code for every test. Replacements at the system's edges go in `subcutaneous(graph, edges = { … })`.

---

## [Robot](../src/main/kotlin/architecture/rules/testing/Robot.kt)

A class extending `ScreenRobot` (`:platform:common:robots`): one screen's user actions and what
the user sees on it. Component Tests and End To End Tests drive screens only through robots.

```kotlin
fun User.onUkptScreen(block: UkptScreenRobot.() -> Unit) =
    on(UkptDestination::class, UkptScreenTags.Screen, ::UkptScreenRobot, block)

class UkptScreenRobot internal constructor(context: RobotContext) : ScreenRobot(context) {
    fun clickGreetButton() = click(UkptScreenTags.GreetButton)
    fun seeGreeting(text: String) = seeText(UkptScreenTags.LatestGreeting, text)
}
```

A test reads as the steps a user takes, one block per screen:

```kotlin
asUser(rootContext) {
    onUkptScreen {
        clickResetButton()
    }
    onConfirmResetDialog {
        clickResetButton()
    }
    onUkptScreen {
        seeNoGreetings()
    }
}
```

`on[Screen] { }` waits until the screen's tag is shown. Given the application's `RootContext`,
as in an End To End Test, it also waits until the screen's destination is the active one, so a
step can't act on a screen behind a dialog.

##### Requirements

* A Robot is a class
* A Robot extends `ScreenRobot`

##### Rules

* A Robot must live in a `:feature:[name]:tests` module's `src/main` source set
    * **Why:** The feature's Component Tests and the application's End To End Tests both depend on its `:tests` module.
* A Robot must reference exactly one Screen Tags object, or one `object [Name]Tags` of a screen in a shared UI module
    * **Why:** A robot drives one screen. A step on another screen belongs in that screen's robot, in its own `on[Screen] { }` block.
    * **Note:** A screen in a shared UI module (`:platform:client:*` or `:app:client:common`), such as a confirmation sheet every feature opens, keeps its tags beside it in an `object [Name]Tags` holding only `const val`s. Those modules have no Constructs, so its robot's tags count by that shape.
* Every public function of a Robot must be named for a user action (`click…`, `enter…`, `clear…`, `select…`, `toggle…`, `scroll…`) or for what the user sees (`see…`)
    * **Why:** A test reads as what a user would report: "I click Greet, then I see Hello again". A name such as `greet()` hides which control the step uses.
* A Robot's file must declare its entry point, a `User.on[Screen]` function
    * **Why:** Tests call a robot only through `on[Screen] { }`, which waits for the screen before the first step.

---

## [Fixture](../src/main/kotlin/architecture/rules/testing/Fixture.kt)

A top-level function or property in test sources that builds test data or performs a shared
test step, such as a builder for a domain model used by several tests. Scenarios and Test
Graphs are their own Constructs.

##### Requirements

* A Fixture satisfies one of: {is a function, is a property}
* A Fixture does not satisfy: is annotated `@Composable`
* A Fixture does not satisfy: has a `Scenario` receiver
* A Fixture does not satisfy: is a top-level property initialised with `SubcutaneousGraph(`
