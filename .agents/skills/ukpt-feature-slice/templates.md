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

    testImplementation(libs.kotlin.testJunit)
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

    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.jsoup)
    testImplementation(libs.udytils.htmlSnapshot)
}
```

Add `projects.platform.server.postgres` when the feature gets its first table.

## §3 — `settings.gradle.kts`

```kotlin
include(":feature:<name>:api")
include(":feature:<name>:server")
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

`<Name>Paths.kt` and `<Name>Ids.kt`:
```kotlin
internal object <Name>Paths {
    const val <THINGS> = "/<name>"
    const val <THING>_EVENTS = "/<name>/events"
}

internal object <Name>Ids {
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

## §7 — Tests (`feature/<name>/server/src/test/kotlin/feature/<name>/server/web/`)

`<Name>RoutesTest.kt`:
```kotlin
class <Name>RoutesTest {

    private val repository = <Thing>Repository()
    private val routes = <Name>Routes(flowOf<Thing>s = repository.flowOf<Thing>s)

    @Test
    fun `the page lists every <thing>`() = testApplication {
        application { installWebPlatform(listOf(routes)) }

        val page = Jsoup.parse(client.get(<Name>Paths.<THINGS>).bodyAsText())

        assertEquals("<Things>", page.select("h1").text())
    }
}
```

`<Thing>sPageSnapshotTest.kt`:
```kotlin
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

- [ ] `settings.gradle.kts` includes `:feature:<name>:api` and `:feature:<name>:server`.
- [ ] `app/server/build.gradle.kts` has `implementation(projects.feature.<name>.server)`.
- [ ] `ServerDependencies.kt` lists `<name>ServerDependencies`; `./gradlew :app:server:test` passes.
- [ ] Goldens recorded with `-PrecordHtmlSnapshots` and reviewed.
- [ ] `./gradlew verifyArchitecture` passes.
- [ ] The page loads in a browser with no console errors.
