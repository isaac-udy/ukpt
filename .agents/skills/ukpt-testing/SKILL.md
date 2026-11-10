---
name: ukpt-testing
description: >-
  Write, place, and run UKPT tests by category — Subcutaneous, Unit, Component,
  Snapshot, Wiring, Adapter, Check, End-to-end — with the category annotations
  from :platform:common:testing, the per-category Gradle tasks, the Subcutaneous
  harness (test graph, scenarios, edges, the htmx tab, coverage), page robots,
  browser End To End Tests, the Testing architecture rules that check a test
  behaves as its category claims, and which tests to run while iterating. Use
  when adding or changing a test, choosing what kind of test to write, choosing
  which tests to run after a change, deciding whether to keep an end-to-end
  test, running one category, or fixing a Testing.* rule failure.
---

# ukpt-testing

Every test class carries exactly one annotation from `platform.testing`. The category says what
the test may use besides the code under test; the `Testing` rules check it
(`platform/common/architecture/docs/testing.md`).

## Choosing a category

| Write a… | When | Where |
| --- | --- | --- |
| `@SubcutaneousTest` | Behaviour a user reaches through a page. **The default.** | `:feature:<name>:tests`, `src/test/` |
| `@UnitTest` | Logic with many inputs: parsing, validation, formatting, a branching UseCase | the module's `src/test/` |
| `@ComponentTest` | Markup with many states, rendered with fixed state: a Component, an event stream's swaps | a `:feature:<name>:server` module's `src/test/` |
| `@SnapshotTest` | A Page's golden HTML | a `:feature:<name>:server` module's `src/test/` |
| `@WiringTest` | The server's Koin graph (`verify()`) | `:app:server`, `src/test/` |
| `@AdapterTest` | One server adapter against real infrastructure, where a Subcutaneous Test reaches poorly | a server module's `src/test/`, or `:platform:common:subcutaneous`'s for the tab |
| `@CheckTest` | Two committed files that must agree | a `src/test/` |
| `@EndToEndTest` | A journey in a real browser: scripts, Alpine, the content security policy | `:app:server`, `src/test/` |

Ask first whether a Subcutaneous Test covers the behaviour. A Unit Test constructs one production
class (`Testing.UnitTest.oneProductionClass`) and imports no DI, network, database, file, HTML or
browser API (`Testing.UnitTest.noInfrastructure`); collaborators are lambdas for `fun interface`s or
`Fake[Name]` classes. Constructing domain models, View States, Forms and other value types is test
data and does not count.

## Writing one

Copy the nearest test of the same category:

- Subcutaneous: `feature/core/tests/src/test/kotlin/feature/ukpt/tests/GreetingsPageTest.kt`
- Robot: `feature/core/tests/src/main/kotlin/feature/ukpt/tests/GreetingsPageRobot.kt`
- Unit: `feature/core/server/src/test/kotlin/feature/ukpt/server/domain/GreetImplTest.kt`
- Component: `feature/core/server/src/test/kotlin/feature/ukpt/server/web/GreetingEventsTest.kt`
- Snapshot: `feature/core/server/src/test/kotlin/feature/ukpt/server/web/GreetingsPageSnapshotTest.kt`
- Wiring: `app/server/src/test/kotlin/com/isaacudy/ukpt/ServerDependenciesTest.kt`
- Adapter: `platform/server/postgres/src/test/kotlin/platform/server/postgres/TransactionRunnerTest.kt`,
  `platform/server/web/src/test/kotlin/platform/server/web/WebPlatformTest.kt`,
  `platform/server/host/src/test/kotlin/platform/server/host/ServerHostTest.kt`,
  `platform/common/subcutaneous/src/test/kotlin/platform/testing/subcutaneous/HtmxTabWebSocketTest.kt`
- End-to-end: `app/server/src/test/kotlin/com/isaacudy/ukpt/GreetingJourneyTest.kt`

The module's test source set needs `testImplementation(projects.platform.common.testing)` and
`libs.kotlin.test`. Tests run on JUnit 5; use `kotlin.test` assertions and `org.junit.jupiter.api`
lifecycle annotations, never `org.junit.Test`.

Every `@Test` function asserts (`Testing.testFunctionsAssert`): an `assert*`, `fail`, `verify`,
`expect*` or `await*` call, or a robot's `see*` call, directly or through a helper declared in the
same file. Every other top-level declaration in test sources is a `Fake[Name]` class or object, a
robot, or a top-level fixture function or property.

## Robots

Subcutaneous Tests and End To End Tests use pages only through robots, and the same robot runs in
both. A robot covers one page and lives in the feature's `:tests` module, `src/main`, from
`:platform:common:robots` (package `platform.testing.robots`).

1. **Ids.** The robot locates elements from the feature's `object [Name]Ids` and
   `object [Name]Paths` in `server.web`, which are public for it. Give an element the robot uses an
   `elementId = [Name]Ids.…` in the markup, or locate it inside one by tag, attribute and text.
2. **Robot.** One class per page, `[Name]PageRobot`, with its entry point in the same file:

   ```kotlin
   fun User.onGreetingsPage(block: GreetingsPageRobot.() -> Unit) =
       on(setOf(UkptPaths.HOME, UkptPaths.GREETINGS), ::GreetingsPageRobot, block)

   class GreetingsPageRobot internal constructor(driver: PageDriver) : PageRobot(driver) {
       private val form = Locator.css(UkptIds.greetingForm.selector)

       fun enterName(name: String) = enter(form.find("input[name=name]"), name)
       fun clickGreetButton() = click(form.find("button[type=submit]", hasText = "Greet"))
   }
   ```

   The entry point lists every path the page renders at: a form posted without scripts re-renders
   the page at the post's path. A `Locator` is a CSS selector, narrowed by the text an element
   contains (`hasText`, case-insensitive), then searched within by `find`. Name each function for
   what a user does or sees: `click…`, `enter…`, `clear…`, `select…`, `toggle…`, `scroll…`, `see…`
   (`Testing.Robot.userActionNames`). `PageRobot` has `click`, `enter`, `clear`, `select`,
   `toggle`, `seeText`, `seeTexts`, `seeValue` and `seeNone`; each waits up to 10 s, and a failure
   names the locator, what it found, the path, and the page's error region.
3. **Use.** One `on[Page] { }` block per page visit, inside `asUser(path) { }`:

   ```kotlin
   asUser(UkptPaths.HOME) {
       onGreetingsPage { seeNoGreetings() }

       given { greetingHistory("Kia ora") }

       onGreetingsPage { seeGreetings("Kia ora") }
   }
   ```

   `on[Page]` waits until the tab shows one of the page's paths, and fails naming the path it shows.
   The `asUser` block suspends, so `given { }` runs between visits.

## Subcutaneous tests

The harness is `:platform:common:subcutaneous` (package `platform.testing.subcutaneous`). A
feature's `:feature:<name>:tests` module applies `ukpt.feature-tests` and depends on the harness,
the robots and the feature's `:server` (`ukpt-feature-slice` scaffolds it). Its `src/main` holds the
test graph, the scenarios and the robots; its `src/test` holds the tests.

**Graph.** One `SubcutaneousGraph` per `:tests` module, production modules only:

```kotlin
val ukptTestGraph = SubcutaneousGraph(
    server = featureServer(ukptServerDependencies),   // + the Postgres modules
)
```

**A test.** `subcutaneous(graph) { … }` is the whole body of each `@Test`. Per test it clones an
empty database from a template migrated once per JVM (`classpath:db/migration`) and starts the
server graph with `installServerHost` on Ktor's test engine. It tears all of it down when the block
returns or throws. `installServerHost` installs the graph's `ServerPlugin` bindings (authentication,
call logging) and its bound `ContentSecurityPolicy`, so the tests run them as production does; a
plugin installed in `Server.kt` instead never reaches the tests.

```kotlin
@Test
fun `a name greeted before is greeted again`() = subcutaneous(ukptTestGraph) {
    given { greetingHistory("Hello, Ada") }

    asUser(UkptPaths.HOME) {
        onGreetingsPage {
            enterName("Ada")
            clickGreetButton()
            seeGreetings("Hello, Ada", "Hello again, Ada")
        }
    }
}
```

- `asUser(path) { }` — opens a tab with its own cookies. It sends what htmx sends for `hx-get`,
  `hx-post` and the other verbs (with `HX-Request`, `HX-Target`, `HX-Current-URL`), swaps the
  response into the inherited `hx-target` as `hx-swap` says, applies out-of-band swaps, follows
  `HX-Redirect`, `HX-Location`, `HX-Retarget`, `HX-Reswap` and `hx-push-url`, and keeps each
  `sse-connect` stream open, swapping its events into the `sse-swap` elements. It opens a websocket
  for each `ws-connect` element, with the tab's cookies, and swaps each text frame as the htmx `ws`
  extension does: every top-level element out of band, by its id unless `hx-swap-oob` names a style
  or a target. A stream or socket opens when its element enters the page under `hx-ext="sse"` or
  `hx-ext="ws"`, and closes when the element leaves it. A robot's `see…` waits for what a frame or
  an event brings. A failed request's text goes into the platform's error region, as `app.js` does;
  a failed stream, socket or frame shows in a robot's failure message. Plain forms and links
  navigate and follow redirects.
- `asUser(path, scripts = false) { }` — the same tab with JavaScript off: forms post, `303`s
  redirect. One such test per form keeps the page working without htmx.
- The tab runs no JavaScript: Alpine and the project's scripts need an End To End Test. An
  attribute that changes what htmx sends or swaps and that the tab does not implement
  (`hx-include`, `hx-select`, `hx-select-oob`, `hx-params`, `hx-encoding`, `hx-prompt`, `ws-send`)
  fails the step that reaches it.
- `given { … }` — runs scenarios, before a page opens or while one is open. A change made mid-test
  reaches an open page through the server's event stream, as in a browser.

**Scenarios.** `suspend fun Scenario.<name>(…)` in the `:tests` module's `src/main`. Inside,
`server<T>()` resolves from the running graph. A scenario writes through server domain interfaces
only and returns what it created (`Testing.Scenario.*`). Another feature's `:tests` module depends
on this one to reuse them.

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
rest well under 1 s.

## End-to-end tests

An End To End Test opens the server's pages in headless Chromium through Playwright, with the same
robots. Write one per journey that depends on a browser: Alpine, the project's scripts, the
content security policy. The behaviour of a page belongs in a Subcutaneous Test.

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

- `inProcessServer(server, edges)` — from the harness. Serves the server graph on a free local port
  over a new database. `given { }` runs scenarios from any `:tests` module the test set depends on.
- `asUserInBrowser(path) { }` — from `:platform:common:browser`. Opens a tab in a new browser
  context, runs the block on the test thread (Playwright's objects are bound to it), and fails
  afterwards if the page logged an error to the console, which is where Chromium reports a content
  security policy violation. Dialogs such as `hx-confirm` are accepted.
- `:app:server` applies `ukpt.end-to-end-tests`, which installs the Chromium headless shell before
  `test` and `endToEndTest` (`./gradlew installEndToEndBrowser`). Its test set depends on
  `:platform:common:browser` and the features' `:tests` modules.

Run them with `./gradlew endToEndTest`. The first test in a JVM takes about 4 s (Postgres and
Chromium start).

## Running a category

A task name given without a module path runs in every module that has it:

```
./gradlew unitTest
./gradlew subcutaneousTest
./gradlew componentTest snapshotTest
./gradlew wiringTest checkTest adapterTest endToEndTest
```

One module: `./gradlew :feature:core:server:unitTest`. The category annotations carry JUnit 5 tags
and timeouts (10 s unit; 30 s component, snapshot, wiring, check; 60 s adapter, subcutaneous;
120 s end-to-end). `test` still runs every category.

## Development loop

Run the narrowest tests that cover the change while iterating, the feature's categories before
handing back, and the full sweep once, before a pull request. CI runs the full sweep on every pull
request (`ukpt-verify`, "CI"). Run builds one at a time.

**After each edit:**

- The test class for the code that changed, with a filter:
  `./gradlew :feature:<name>:tests:subcutaneousTest --tests '*<Page>PageTest'`. Gradle compiles only
  what that test needs.
- `./gradlew verifyArchitecture` after adding, moving or renaming a declaration.
- Not `test`, `check` or a category task without a module path: each runs every module.

**Before handing back**, the categories of the modules that changed:

| Changed | Run |
| --- | --- |
| A Routes class, a Page, a Component, a Form, or what they call in `server.domain`/`server.data` | `:feature:<name>:tests:subcutaneousTest` |
| Storage in `server.data.storage`, or a migration | `:feature:<name>:tests:subcutaneousTest`; `adapterTest` in the module whose storage changed |
| A Page's markup | `:feature:<name>:server:snapshotTest`, re-recorded with `-PrecordHtmlSnapshots` only after an intended change |
| A robot | `:feature:<name>:tests:subcutaneousTest` and `:app:server:endToEndTest` |
| An event stream or Component with a Component Test | that module's `componentTest` |
| Logic with a Unit Test | that module's `unitTest` |
| A Koin module or a constructor | `:app:server:wiringTest` |
| A script, an Alpine component, `:platform:server:web`'s document or security headers | `:app:server:endToEndTest`; `:platform:server:web:adapterTest` |
| The Subcutaneous tab or `installServerHost` | `:platform:common:subcutaneous:adapterTest`, `:platform:server:host:adapterTest`, and the `:tests` modules' `subcutaneousTest` |

Then `verifyArchitecture` and `validateTemplate`.

**Timings** on a 16 GB M1 Pro, with the Gradle daemon running and the code compiled, the test task
forced to run (`--rerun`), in seconds:

| Run | s |
| --- | --- |
| `validateTemplate` | 2 |
| `verifyArchitecture` | 5 |
| `:feature:core:tests:subcutaneousTest`, all 9 tests | 14 |
| `:feature:core:server:test` (unit, component, snapshot) | 3 |
| `:app:server:endToEndTest`, 2 tests | 12 |
| `:app:server:wiringTest` | 3 |
| every category, every module, one invocation | 20 |

Most of a Subcutaneous run is starting the test JVM and Postgres, and most of an End To End run is
starting Chromium, so a `--tests` filter saves time only in a module with many tests. Name several
tasks in one `./gradlew` call rather than running them one after another.

### Checking a feature: by hand, a throwaway test, or a kept test

- **By hand** (`ukpt-run`, then a browser): layout, spacing, how a page looks and responds. Nothing
  re-runs it, so it is not a test of behaviour.
- **A throwaway End To End Test:** a journey across several pages, or one that needs a script to
  run, that would take many steps to repeat by hand after each change. Write it in `:app:server`'s
  `src/test` with the feature's robots, run it while iterating, and delete it before committing
  unless it meets the next point.
- **A kept End To End Test:** one per main journey of the app, for what a Subcutaneous Test can't
  reach: htmx and Alpine running in a browser, the project's scripts, the content security policy.
  A behaviour inside one page, even one an End To End Test found, gets a Subcutaneous Test.

## Rule failures

`./gradlew :platform:common:architecture:verifyArchitecture` checks every rule. A `Testing.*`
failure names the rule; its statement and rationale are in
`platform/common/architecture/docs/testing.md`.

- `Testing.oneCategoryPerTestClass` — add the one annotation.
- `architecture.everyTestDeclarationBelongsToATestConstruct` — a helper class needs to be a
  `Fake[Name]`, a top-level function, or part of the test class.
- `Testing.*.placement` — move the file to the source set the table above names.
- `Testing.ComponentTest.rendersMarkupOnly` — a Routes class, a repository or a running application
  in a Component Test makes it a Subcutaneous Test.
- `ServerWeb.Page.hasARobot` — add `[Name]PageRobot` to the feature's `:tests` module.
- `Testing.Robot.usedBySubcutaneousTests` — add a Subcutaneous Test that calls the robot's
  `on[Page]`.
- `Testing.SubcutaneousTest.entryPointVocabularyOnly` — replace the Routes class, page function,
  repository or use case with the path and the robot that reach it, and seed through a scenario.
- `Testing.SubcutaneousTest.runsInTheHarness` — run the body in `subcutaneous(graph) { … }` and use
  the page through `asUser(path) { … }`.
- `Testing.SubcutaneousTest.noInfrastructureImports` — move the binding to `edges`, the setup to a
  scenario, the request or the jsoup lookup into the page's robot.
- `Testing.Scenario.seedsThroughServerDomain` — call a server domain interface instead of storage.
- `ModuleRules.testsModuleShape` — add the feature's `:server` to the `:tests` build file.
- `Testing.EndToEndTest.drivesTheServerInABrowser` — run the test body in `inProcessServer(…)` and
  use the pages through `asUserInBrowser(path) { … }`.
- `Testing.EndToEndTest.drivesThroughRobots` — move the Playwright, jsoup or HTTP call into the
  page's robot and call it from an `on[Page] { }` block.
- `Testing.Robot.onePageEntryPoint` — declare one `fun User.on[Name]Page(block: [Name]PageRobot.() -> Unit)`
  in the robot's file; move another page's steps to that page's robot.
- `Testing.Robot.userActionNames` — rename for the control and the action: `greet()` →
  `clickGreetButton()`.
