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

On the JVM each annotation carries a JUnit 5 tag and timeout. `./gradlew unitTest` runs the
unit tests of every module; each category has its task (`subcutaneousTest`, `componentTest`,
`wiringTest`, `adapterTest`, `checkTest`, `endToEndTest`, and `snapshotTest`, which needs
`--no-configuration-cache`). kotlin.test has no tags on JS, wasm or iOS, so `componentTest`
also runs the module's `jsTest`.

Test-support declarations (fakes, fixtures, snapshot surfaces) are Constructs too: every
top-level declaration in test sources matches exactly one.

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
* [Fixture](#fixture)

##### Rules

* Every test class (a class declaring a `@Test` function or extending `PreviewSnapshotTestCase`) must carry exactly one test category annotation
    * **Why:** The category decides which task runs the test and which rules check it.
* Every `@Test` function must assert: call an `assert*`, `fail`, `verify`, `expect*` or `await*` function, directly or through a function declared in the same file
    * **Why:** A test without an assertion passes whatever the code does. It reports coverage that does not exist.
    * **Note:** Detection is by name in the source text, so an assertion helper declared in another file needs a name that matches.

---

## [Subcutaneous Test](../src/main/kotlin/architecture/rules/testing/SubcutaneousTest.kt)

A `@SubcutaneousTest` class: drives a real entry point (a ViewModel, or a `@Urpc` service for
server behaviour that no screen reaches) built from the production DI graph, with the real
server running in-process against an embedded database. It asserts on what the entry point
exposes: ViewModel state, navigation results, service responses.

Subcutaneous tests are the main suite. They exercise the code a user reaches through a screen,
and do not change when the domain and data layers below the ViewModel are restructured.

##### Requirements

* A Subcutaneous Test is a class
* A Subcutaneous Test is annotated `@SubcutaneousTest`

##### Rules

* A Subcutaneous Test must live in the `src/test` source set of a `:feature:[name]:tests` module
    * **Why:** The `:tests` module depends on the feature's `:client` and `:server`, which no other module may combine.

---

## [Unit Test](../src/main/kotlin/architecture/rules/testing/UnitTest.kt)

A `@UnitTest` class: one production class exercised over many inputs, with every collaborator
a fake declared in test sources or a lambda.

A unit test is for logic whose input space is wider than the screens that reach it: parsers,
validators, formatters, reducers, a UseCase with many branches. Behaviour that a user reaches
through a screen is covered by a [Subcutaneous Test](#subcutaneous-test) instead, which does
not change when the layers between the ViewModel and the database are restructured.

##### Requirements

* An Unit Test is a class
* An Unit Test is annotated `@UnitTest`

##### Rules

* A Unit Test must live in a test source set other than `androidHostTest`, outside a `:feature:[name]:tests` module
    * **Why:** `androidHostTest` runs Paparazzi; a `:tests` module holds Subcutaneous Tests.
* A Unit Test must not import dependency injection, network, database, file-system, or UI-test APIs
    * **Why:** Those APIs are what the other categories use. A unit test that reaches them is a Wiring, Adapter, Subcutaneous or Component test, and is annotated as one.
* A Unit Test must construct at most one production class; constructing domain models, states and other value types is test data
    * **Why:** A test that assembles several production classes is testing their combination, which is what a Subcutaneous Test covers with the real dependency graph.
    * **Note:** Constructed means the class name followed by `(` in the test file's code.

---

## [Component Test](../src/main/kotlin/architecture/rules/testing/ComponentTest.kt)

A `@ComponentTest` class: renders one screen's `[Name]ScreenContent` with fixed state, then
reads and drives the rendered output. The Compose HTML screens are tested this way in
`src/jsTest/` with `html-test-utils`.

##### Requirements

* A Component Test is a class
* A Component Test is annotated `@ComponentTest`

##### Rules

* A Component Test must live in a `:client` module's `jsTest`, `jvmTest` or `desktopTest` source set
    * **Why:** `[Name]ScreenContent` is `internal` to its `:client` module.
* A Component Test must reference only composables, ViewModel states, and the value types they carry from production code, and must not import dependency injection
    * **Why:** The test fixes the state and reads what renders. A ViewModel, a repository, or the DI graph in the test makes it a Subcutaneous Test.

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

##### Requirements

* An End To End Test is a class
* An End To End Test is annotated `@EndToEndTest`

##### Rules

* An End To End Test must live in `:app:client:desktop`'s `src/test` source set
    * **Why:** The desktop shell is the one application that runs the UI and the server in one JVM.

---

## [Fake](../src/main/kotlin/architecture/rules/testing/Fake.kt)

A class or object in test sources named `Fake[Name]`: a hand-written stand-in for a production
interface. A `fun interface` collaborator is faked with a lambda instead, which needs no
declaration.

##### Requirements

* A Fake satisfies one of: {is a class, is an object}
* A Fake is named `Fake[Name]`
* A Fake declares no `@Test` function

---

## [Snapshot Surface](../src/main/kotlin/architecture/rules/testing/SnapshotSurface.kt)

A `@Composable` function in `androidHostTest`: a preview that exists only to be snapshotted,
such as a design-system doc surface, and the frame it renders in.

##### Requirements

* A Snapshot Surface is a function
* A Snapshot Surface is annotated `@Composable`
* A Snapshot Surface lives in `androidHostTest`

---

## [Fixture](../src/main/kotlin/architecture/rules/testing/Fixture.kt)

A top-level function or property in test sources that builds test data or performs a shared
test step, such as a builder for a domain model used by several tests.

##### Requirements

* A Fixture satisfies one of: {is a function, is a property}
* A Fixture does not satisfy: is annotated `@Composable`
