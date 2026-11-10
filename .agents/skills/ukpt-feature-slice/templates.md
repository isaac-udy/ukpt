# ukpt-feature-slice templates

Substitute `<name>` (lowercase feature/package segment), `<Name>` (PascalCase type prefix), and
`<Thing>`/`<thing>` for the feature's own model. These mirror `:feature:core` — if core's files
change materially, update these to match.

## §1 — `:api` build → `feature/<name>/api/build.gradle.kts`

```kotlin
plugins {
    id("ukpt.jvm-library")
}

dependencies {
    api(libs.kotlinx.coroutinesCore)
    api(libs.kotlinx.serialization)
    api(libs.kotlinx.datetime)
    api(libs.udytils.core)

    testImplementation(projects.platform.common.testing)
    testImplementation(libs.kotlin.test)
}
```

`:api` holds what other features may use: the shared models in `feature.<name>`, and any
`server.domain` interface another feature injects. It starts with the feature's models only.

## §2 — `:server` build → `feature/<name>/server/build.gradle.kts`

```kotlin
plugins {
    id("ukpt.jvm-library")
    id("ukpt.html-snapshot")
}

dependencies {
    api(projects.feature.<name>.api)

    implementation(libs.udytils.architectureAnnotations)

    implementation(projects.platform.server.web)
    implementation(libs.ktor.serverCore)
    implementation(libs.udytils.core)
    implementation(libs.koin.core)

    testImplementation(projects.platform.common.testing)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.jsoup)
    testImplementation(libs.udytils.htmlSnapshot)
}
```

Add `projects.platform.server.postgres` when the feature gets its first table.

`:tests` build → `feature/<name>/tests/build.gradle.kts`:
```kotlin
plugins {
    id("ukpt.feature-tests")
}

dependencies {
    api(projects.platform.common.subcutaneous)
    api(projects.platform.common.robots)
    implementation(projects.feature.<name>.server)
}
```

## §3 — `settings.gradle.kts`

```kotlin
include(":feature:<name>:api")
include(":feature:<name>:server")
include(":feature:<name>:tests")
```

## §4 — Domain and data

`feature/<name>/api/src/main/kotlin/feature/<name>/<Thing>.kt`:
```kotlin
package feature.<name>

import kotlinx.serialization.Serializable

@Serializable
data class <Thing>(
    val id: Long,
    val title: String,
)
```

`feature/<name>/server/src/main/kotlin/feature/<name>/server/domain/FlowOf<Thing>s.kt`:
```kotlin
package feature.<name>.server.domain

import feature.<name>.<Thing>
import kotlinx.coroutines.flow.Flow

fun interface FlowOf<Thing>s {
    operator fun invoke(): Flow<List<<Thing>>>
}
```

`feature/<name>/server/src/main/kotlin/feature/<name>/server/data/<Thing>Repository.kt` — an
in-memory Repository until the feature has a table (`:feature:core`'s `GreetingRepository` is the
worked example; `platform/common/architecture/docs/serverdata.md` covers Storage classes and transactions):
```kotlin
package feature.<name>.server.data

import dev.isaacudy.udytils.state.RepositoryState
import dev.isaacudy.udytils.state.repositoryState
import feature.<name>.<Thing>
import feature.<name>.server.domain.FlowOf<Thing>s

internal class <Thing>Repository {

    private val things: RepositoryState<<Thing>Repository, List<<Thing>>> = repositoryState(emptyList())

    val flowOf<Thing>s = FlowOf<Thing>s { things }
}
```

## §5 — Web layer (`feature/<name>/server/src/main/kotlin/feature/<name>/server/web/`)

`<Name>Paths.kt` and `<Name>Ids.kt`, public for the `:tests` module's robots:
```kotlin
object <Name>Paths {
    const val <THINGS> = "/<name>"
    const val <THING>_EVENTS = "/<name>/events"
}

object <Name>Ids {
    val <thing>s = ElementId("<name>-<thing>s")

    const val <THING>S_EVENT = "<thing>s"

    fun <thing>(id: Long): ElementId = ElementId("<name>-<thing>-$id")
}
```

`<Thing>sPageState.kt`:
```kotlin
internal data class <Thing>sPageState(
    val <thing>s: List<<Thing>>,
)
```

`<Thing>sPage.kt` — the Page and its Components:
```kotlin
internal fun HTML.<thing>sPage(state: <Thing>sPageState) {
    ukptLayout(LayoutState(title = "<Things>")) {
        h1 { +"<Things>" }
        <thing>List(state.<thing>s)
    }
}

internal fun FlowContent.<thing>List(<thing>s: List<<Thing>>) {
    ul("list") {
        elementId = <Name>Ids.<thing>s
        <thing>s.forEach { <thing>Item(it) }
    }
    div {
        sse {
            connect(<Name>Paths.<THING>_EVENTS)
            swap(<Name>Ids.<THING>S_EVENT)
        }
        hx { swap(SwapStyle.None) }
    }
}

internal fun UL.<thing>Item(<thing>: <Thing>) {
    li {
        elementId = <Name>Ids.<thing>(<thing>.id)
        +<thing>.title
    }
}
```

`<Thing>Events.kt`:
```kotlin
internal fun <thing>Events(flowOf<Thing>s: FlowOf<Thing>s): Flow<ServerSentEvent> {
    val list = OobList.unorderedList<<Thing>, Long>(<Name>Ids.<thing>s, key = { it.id }) { <thing>Item(it) }
    return flowOf<Thing>s()
        .oobUpdates(list)
        .map { ServerSentEvent(data = it, event = <Name>Ids.<THING>S_EVENT) }
}
```

`<Name>Routes.kt`:
```kotlin
internal class <Name>Routes(
    private val flowOf<Thing>s: FlowOf<Thing>s,
) : WebRoutes {

    override fun Route.install() {
        get(<Name>Paths.<THINGS>) {
            val <thing>s = flowOf<Thing>s().first()
            call.respondHtml { <thing>sPage(<Thing>sPageState(<thing>s)) }
        }

        sse(<Name>Paths.<THING>_EVENTS) {
            <thing>Events(flowOf<Thing>s).collect { send(it) }
        }
    }
}
```

A form — its `<Thing>Form` with `from(Parameters)` and `validate()`, the Component that renders it,
and the `post` handler answering `422`, a fragment, or a `303` — copies `GreetingForm`,
`greetingForm` and the `post(UkptPaths.GREETINGS)` handler in `:feature:core`.

## §6 — DI → `feature/<name>/server/src/main/kotlin/feature/<name>/<Name>ServerDependencies.kt`

```kotlin
package feature.<name>

val <name>ServerDependencies = module {
    singleOf(::<Thing>Repository)
    single<FlowOf<Thing>s> { get<<Thing>Repository>().flowOf<Thing>s }

    singleOf(::<Name>Routes) bind WebRoutes::class
}
```

## §7 — Tests

`:feature:<name>:tests`, `src/main/kotlin/feature/<name>/tests/`:

`<Name>TestGraph.kt`:
```kotlin
package feature.<name>.tests

val <name>TestGraph = SubcutaneousGraph(
    server = featureServer(<name>ServerDependencies),
)
```

`<Thing>Scenarios.kt`, written through the server domain:
```kotlin
suspend fun Scenario.<thing>s(vararg names: String): List<<Thing>> {
    val update<Thing>s = server<Update<Thing>s>()
    names.forEach { update<Thing>s.add(it) }
    return server<FlowOf<Thing>s>()().first()
}
```

`<Thing>sPageRobot.kt`, one per Page; the entry point lists every path the page renders at:
```kotlin
fun User.on<Thing>sPage(block: <Thing>sPageRobot.() -> Unit) = on(<Name>Paths.<THINGS>, ::<Thing>sPageRobot, block)

class <Thing>sPageRobot internal constructor(driver: PageDriver) : PageRobot(driver) {
    private val list = Locator.css("${<Name>Ids.<thing>s.selector} > li")

    fun see<Thing>s(vararg names: String) = seeTexts(list, names.toList())
}
```

`src/test/kotlin/feature/<name>/tests/<Thing>sPageTest.kt`:
```kotlin
@SubcutaneousTest
class <Thing>sPageTest {

    @Test
    fun `the page lists every <thing>`() = subcutaneous(<name>TestGraph) {
        given { <thing>s("First", "Second") }

        asUser(<Name>Paths.<THINGS>) {
            on<Thing>sPage { see<Thing>s("First", "Second") }
        }
    }
}
```

`:feature:<name>:server`, `src/test/kotlin/feature/<name>/server/web/`:

`<Thing>sPageSnapshotTest.kt`:
```kotlin
@SnapshotTest
class <Thing>sPageSnapshotTest {

    private val snapshots = HtmlSnapshots()

    @Test
    fun empty() {
        snapshots.assertMatches("<Thing>sPage/empty", render(<Thing>sPageState(emptyList())))
    }

    private fun render(state: <Thing>sPageState): String = "<!DOCTYPE html>" + createHTML().html { <thing>sPage(state) }
}
```

## §8 — Wiring checklist

- [ ] `settings.gradle.kts` includes `:feature:<name>:api`, `:feature:<name>:server` and `:feature:<name>:tests`.
- [ ] `app/server/build.gradle.kts` has `implementation(projects.feature.<name>.server)`, and
      `testImplementation(projects.feature.<name>.tests)` when an End To End Test drives its pages.
- [ ] `ServerDependencies.kt` lists `<name>ServerDependencies`; `./gradlew :app:server:wiringTest` passes.
- [ ] Every Page has a `<Page>PageRobot` that a Subcutaneous Test uses; `./gradlew :feature:<name>:tests:subcutaneousTest` passes.
- [ ] Goldens recorded with `-PrecordHtmlSnapshots` and reviewed.
- [ ] `./gradlew verifyArchitecture` passes.
