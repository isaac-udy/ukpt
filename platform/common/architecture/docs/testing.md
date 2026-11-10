> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/testing/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Testing](../src/main/kotlin/architecture/rules/testing/Testing.kt)

_This RuleGroup governs test sources._

Every test class declares one category with an annotation from `:platform:common:testing`. The
category states what the test may use besides the code under test, which decides how much a
run costs and what a failure means.

| Category | Exercises | Uses for real | Runs on |
| --- | --- | --- | --- |
| `@SubcutaneousTest` | pages, driven through [Robots](#robot), from the production DI graph | everything below the browser: the server in-process, an embedded database, htmx's requests and swaps | JVM |
| `@UnitTest` | one production class over many inputs | nothing; collaborators are test fakes or lambdas | JVM |
| `@ComponentTest` | one page, component or event stream, with fixed state | kotlinx.html | JVM |
| `@SnapshotTest` | pages, compared with golden HTML | kotlinx.html | JVM |
| `@WiringTest` | a DI graph | the DI container | JVM |
| `@AdapterTest` | one infrastructure adapter | the infrastructure | JVM |
| `@CheckTest` | two committed artifacts | files | JVM |
| `@EndToEndTest` | the server through a browser | the whole application, and Chromium | JVM |

Subcutaneous tests are the main suite: most behaviour is covered by using a page as a user
does and reading what it shows. A unit test is for logic with many inputs. A test that needs
another kind of coverage states it with its category.

Each annotation carries a JUnit 5 tag and timeout. `./gradlew unitTest` runs the unit tests of
every module; each category has its task (`subcutaneousTest`, `componentTest`, `snapshotTest`,
`wiringTest`, `adapterTest`, `checkTest`, `endToEndTest`).

Subcutaneous Tests and End To End Tests drive pages through [Robots](#robot), one per page,
named for what the user does: `clickGreetButton()`, `seeGreetings("Hello, Ada")`. The same
robot runs in both.

Test-support declarations (fakes, fixtures, scenarios, test graphs, robots) are Constructs too:
every top-level declaration in test sources matches exactly one.

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
* [Scenario](#scenario)
* [Test Graph](#test-graph)
* [Robot](#robot)
* [Fixture](#fixture)

##### Rules

* Every test class (a class declaring a `@Test` function) must carry exactly one test category annotation
    * **Why:** The category decides which task runs the test and which rules check it.
* Every `@Test` function must assert: call an `assert*`, `fail`, `verify`, `expect*` or `await*` function, or a Robot's `see*` function, directly or through a function declared in the same file
    * **Why:** A test without an assertion passes whatever the code does. It reports coverage that does not exist.
    * **Note:** Detection is by name in the source text, so an assertion helper declared in another file needs a name that matches.

---

## [Subcutaneous Test](../src/main/kotlin/architecture/rules/testing/SubcutaneousTest.kt)

A `@SubcutaneousTest` class: uses the feature's pages as a user does, through their
[Robots](#robot), against the production DI graph with the real server running in-process on
an embedded database. It asserts on what the pages show.

Subcutaneous tests are the main suite. They exercise the code a user reaches through a page, and
do not change when the web, domain and data layers below the page are restructured.

```kotlin
@SubcutaneousTest
class GreetingsPageTest {
    @Test
    fun `a greeting added elsewhere appears on an open page`() = subcutaneous(ukptTestGraph) {
        asUser(UkptPaths.HOME) {
            onGreetingsPage { seeNoGreetings() }

            given { greetingHistory("Kia ora") }

            onGreetingsPage { seeGreetings("Kia ora") }
        }
    }
}
```

`subcutaneous(graph)` gives each test a new database cloned from the migrated template and
runs the [Test Graph](#test-graph)'s modules in-process on Ktor's test engine.
`asUser(path) { }` opens a tab on that server: it sends the requests htmx sends, swaps the
responses, applies out-of-band swaps and follows event streams, and runs no JavaScript;
`asUser(path, scripts = false)` checks that the page works without it. `given { }` runs
[Scenarios](#scenario), before a page opens or while one is open. Each robot step waits up to 10
seconds of real time.

##### Requirements

* A Subcutaneous Test is a class
* A Subcutaneous Test is annotated `@SubcutaneousTest`

##### Rules

* A Subcutaneous Test must live in the `src/test` source set of a `:feature:[name]:tests` module
    * **Why:** The `:tests` module holds the feature's test graph, scenarios and robots beside the tests that use them.
* A Subcutaneous Test must reference only entry-point vocabulary from production code: Web Constants (the paths a user opens), shared domain models and their extensions, domain models, exceptions and constants
    * **Why:** The test opens a path and reads what the page shows through a robot. A reference to a Routes class, a page function, a use case or a repository fixes that declaration in place, and the test changes when the layers below the page are restructured.
    * **Note:** Test data comes from Scenarios (`given { }`), which may use the server domain.
* Every `@Test` function of a Subcutaneous Test must run its body in `subcutaneous(graph) { … }` and use a page through `asUser(`
    * **Why:** The harness starts the server against a new database and tears it down after the test; `asUser` is the tab the robots drive.
* A Subcutaneous Test must not import Koin, Ktor, Exposed, JDBC, jsoup, Playwright or `kotlinx.coroutines.test`
    * **Why:** The harness owns the graph, the server, the database and the tab, and robots own the markup. A request sent by hand skips the markup that would send it, so the test passes while the page's form posts somewhere else. Virtual time from `kotlinx.coroutines.test` does not advance real I/O.

---

## [Unit Test](../src/main/kotlin/architecture/rules/testing/UnitTest.kt)

A `@UnitTest` class: one production class exercised over many inputs, with every collaborator
a fake declared in test sources or a lambda.

A unit test is for logic whose input space is wider than the pages that reach it: parsers,
validators, formatters, a UseCase with many branches. Behaviour that a user reaches through a
page is covered by a [Subcutaneous Test](#subcutaneous-test) instead, which does not change when
the layers between the page and the database are restructured.

##### Requirements

* An Unit Test is a class
* An Unit Test is annotated `@UnitTest`

##### Rules

* A Unit Test must live in a `src/test` source set outside a `:feature:[name]:tests` module
    * **Why:** A `:tests` module holds Subcutaneous Tests.
* A Unit Test must not import dependency injection, network, database, file-system, HTML-parsing or browser APIs
    * **Why:** Those APIs are what the other categories use. A unit test that reaches them is a Wiring, Adapter, Subcutaneous or Component test, and is annotated as one.
* A Unit Test must construct at most one production class; constructing domain models, states and other value types is test data
    * **Why:** A test that assembles several production classes is testing their combination, which is what a Subcutaneous Test covers with the real dependency graph.
    * **Note:** Constructed means the class name followed by `(` in the test file's code.

---

## [Component Test](../src/main/kotlin/architecture/rules/testing/ComponentTest.kt)

A `@ComponentTest` class: renders one Page, Component or Event Stream from `server.web` with
fixed state, then reads the markup with jsoup.

```kotlin
@Test
fun `the first event replaces the list, later events append and remove`() = runTest {
    val events = greetingEvents { flowOf(listOf(ada), listOf(ada, grace)) }.toList()
    …
}
```

A domain interface the rendered code takes is a lambda. A Component Test is for markup with
many states, such as the out-of-band swaps an event stream sends; what a user sees on a page is
covered by a [Subcutaneous Test](#subcutaneous-test).

##### Requirements

* A Component Test is a class
* A Component Test is annotated `@ComponentTest`

##### Rules

* A Component Test must live in the `src/test` source set of a `:feature:[name]:server` module or `:platform:server:web`
    * **Why:** The pages, components and event streams of `server.web` are `internal` to their module.
* A Component Test must reference only the web layer's markup, its View States and Forms, domain interfaces (as lambdas), and the value types they carry, and must not import dependency injection or Ktor's test engine
    * **Why:** The test fixes the state and reads what renders. A Routes class, a repository, the DI graph or a running application in the test makes it a Subcutaneous or Adapter Test.

---

## [Snapshot Test](../src/main/kotlin/architecture/rules/testing/SnapshotTest.kt)

A `@SnapshotTest` class: renders pages with fixed View States and compares each with its
committed golden HTML, through `HtmlSnapshots` from `dev.isaacudy.udytils:html-snapshot`. A
module applies `ukpt.html-snapshot`; goldens live in `src/test/snapshots/html`.

```
./gradlew :feature:core:server:snapshotTest -PrecordHtmlSnapshots
./gradlew snapshotTest
```

##### Requirements

* A Snapshot Test is a class
* A Snapshot Test is annotated `@SnapshotTest`

##### Rules

* A Snapshot Test must live in the `src/test` source set of a `:feature:[name]:server` module or `:platform:server:web`
    * **Why:** `ukpt.html-snapshot` keeps the goldens beside the module's tests, and the pages it renders are `internal` to the module.
* A Snapshot Test must compare through `HtmlSnapshots`
    * **Why:** The harness normalises the markup before comparing, and `-PrecordHtmlSnapshots` rewrites its goldens.

---

## [Wiring Test](../src/main/kotlin/architecture/rules/testing/WiringTest.kt)

A `@WiringTest` class: resolves an application shell's DI graph with Koin's `verify()`, which
checks that every registered constructor's parameters have a binding. `:app:server` keeps its
module list in `ServerDependencies.kt` so one test covers it.

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
for behaviour a Subcutaneous Test reaches poorly: transaction semantics, concurrency, a query's
edge cases. `TransactionRunnerTest` runs against an embedded Postgres.

##### Requirements

* An Adapter Test is a class
* An Adapter Test is annotated `@AdapterTest`

##### Rules

* An Adapter Test must live in the `src/test` source set of a `:platform:server:*` or `:feature:[name]:server` module
    * **Why:** The adapters an Adapter Test checks are server infrastructure.

##### Guidance

* Prefer a Subcutaneous Test; an Adapter Test is for behaviour a Subcutaneous Test cannot reach
    * **Why:** Each Adapter Test fixes an adapter's interface in place, so restructuring the adapter changes the test.
    * **Audited:** a test reports non-conforming code without ever failing.

---

## [Check Test](../src/main/kotlin/architecture/rules/testing/CheckTest.kt)

A `@CheckTest` class: checks that two committed artifacts agree, such as a stylesheet's tokens
and the Kotlin constants that name them. It tests the project, not application behaviour.

##### Requirements

* A Check Test is a class
* A Check Test is annotated `@CheckTest`

##### Rules

* A Check Test must live in a `src/test` source set
    * **Why:** A check reads files from disk, which the JVM test tasks can do.

---

## [End To End Test](../src/main/kotlin/architecture/rules/testing/EndToEndTest.kt)

An `@EndToEndTest` class: drives the server's pages in headless Chromium through Playwright,
against the server serving on a local port. It covers what a Subcutaneous Test does not: the
scripts on a page (htmx itself, Alpine, the project's own files), the content security policy,
and the server starting from `serverDependencies`.

```kotlin
@Test
fun `greeting someone`() = inProcessServer(::serverDependencies) {
    given { greetingHistory("Hello, Grace") }

    asUserInBrowser(UkptPaths.HOME) {
        onGreetingsPage {
            enterName("Ada")
            seeCharactersLeft("37 characters left")
            clickGreetButton()
            seeGreetings("Hello, Grace", "Hello, Ada")
        }
    }
}
```

`inProcessServer(server)` starts the server graph on a free port over a new database.
`asUserInBrowser(path) { }` opens a browser tab there and fails afterwards if the page logged
an error to the console, which is where Chromium reports a content security policy violation.
Each step goes through a page's [Robot](#robot), the same one the Subcutaneous Tests use.
`ukpt.end-to-end-tests` installs the Chromium headless shell before the tests run.

##### Requirements

* An End To End Test is a class
* An End To End Test is annotated `@EndToEndTest`

##### Rules

* An End To End Test must live in `:app:server`'s `src/test` source set
    * **Why:** `:app:server` assembles the whole graph, `serverDependencies`, that the test serves.
* Every `@Test` function of an End To End Test must call `inProcessServer(` and `asUserInBrowser(`
    * **Why:** The test covers the application as a browser loads it, against the real server. A test that sends requests itself, or drives the Subcutaneous tab, is a Subcutaneous Test.
* An End To End Test must find and act on elements only through Robots: no Playwright, jsoup or Ktor client imports
    * **Why:** A journey reads as one block of user steps per page, and a page's markup changes in one place, its robot.

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

## [Scenario](../src/main/kotlin/architecture/rules/testing/Scenario.kt)

A function with a `Scenario` receiver, in a `:feature:[name]:tests` module's `src/main`: puts
the test database into a state through the feature's server domain interfaces. A test calls it
inside `given { }`, before a user opens a page or while one is open; it returns what it created.

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
    * **Why:** A row written past the domain skips the invariants the server enforces, and the test then covers a state the application cannot reach. A Scenario that calls the domain also stays valid when the storage below it changes.

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
    server = featureServer(ukptServerDependencies),
)
```

`featureServer` adds the Postgres modules. A test
replaces a binding at the system's edges (a backend the project does not own, a clock) with
`subcutaneous(graph, edges = { server { … } })`, not in the graph.

##### Requirements

* A Test Graph is a top-level property initialised with `SubcutaneousGraph(`

##### Rules

* A `:feature:[name]:tests` module must declare exactly one Test Graph, and a Test Graph must live in one
    * **Why:** The graph repeats the module list the `:app` shells install for the feature. A second graph is a second list to keep in step with them.
* A Test Graph must list production Koin modules only: each feature's `[name]ServerDependencies`, and `:platform` modules
    * **Why:** A binding declared in the graph replaces production code for every test. Replacements at the system's edges go in `subcutaneous(graph, edges = { … })`.

---

## [Robot](../src/main/kotlin/architecture/rules/testing/Robot.kt)

A class extending `PageRobot` (`:platform:common:robots`): one page's user actions and what the
user sees on it, `[Name]PageRobot`. Subcutaneous Tests and End To End Tests drive pages only
through robots, and the same robot runs in both.

```kotlin
fun User.onGreetingsPage(block: GreetingsPageRobot.() -> Unit) =
    on(setOf(UkptPaths.HOME, UkptPaths.GREETINGS), ::GreetingsPageRobot, block)

class GreetingsPageRobot internal constructor(driver: PageDriver) : PageRobot(driver) {
    private val form = Locator.css(UkptIds.greetingForm.selector)

    fun enterName(name: String) = enter(form.find("input[name=name]"), name)
    fun clickGreetButton() = click(form.find("button[type=submit]", hasText = "Greet"))
}
```

`on[Name]Page { }` waits until the tab shows one of the page's paths: a form posted without
scripts re-renders the page at the post's path. A `Locator` is a CSS selector, narrowed by the
text an element contains; robots build them from the feature's `[Name]Ids` so a robot and its
page name an element with one string. A test reads as the steps a user takes, one block per
page visit.

##### Requirements

* A Robot is a class
* A Robot extends `PageRobot`

##### Rules

* A Robot must live in a `:feature:[name]:tests` module's `src/main` source set
    * **Why:** The feature's Subcutaneous Tests and the application's End To End Tests both depend on its `:tests` module.
* A Robot's file must declare exactly one entry point, `fun User.on[Name]Page(block: [Name]PageRobot.() -> Unit)`, named for the robot
    * **Why:** Tests reach a robot only through its entry point, which waits for the page before the first step. A step on another page belongs in that page's robot, in its own `on[Page] { }` block.
* Every public function of a Robot must be named for a user action (`click…`, `enter…`, `clear…`, `select…`, `toggle…`, `scroll…`) or for what the user sees (`see…`)
    * **Why:** A test reads as what a user would report: "I click Greet, then I see Hello again". A name such as `greet()` hides which control the step uses.
* A Robot's entry point must be called by a Subcutaneous Test
    * **Why:** The Subcutaneous Tests are a page's coverage. A robot that only End To End Tests use leaves the page's behaviour to the slowest suite.

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
