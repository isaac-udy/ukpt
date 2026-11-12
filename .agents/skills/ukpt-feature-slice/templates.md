# ukpt-feature-slice templates

Substitute `<name>` (lowercase feature/package segment) and `<Name>` (PascalCase type prefix).
These mirror `:feature:core` — if core's build files change materially, update these to match.

## §1 — `:api` build → `feature/<name>/api/build.gradle.kts`

```kotlin
plugins {
    id("ukpt.kmp-library")
    id("ukpt.html-web")
    alias(libs.plugins.kotlinKsp)
}

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "feature.<name>.api"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.websockets)

            api(libs.kotlinx.coroutinesCore)
            api(libs.kotlinx.serialization)
            api(libs.kotlinx.datetime)
            api(libs.enro.common)
            api(libs.udytils.core)
            // urpc contract types referenced by the KSP-generated client/binding/descriptors.
            // `api` so :client and :server see them transitively.
            api(libs.urpc.protocol)
        }
        commonTest.dependencies {
            implementation(projects.platform.common.testing)
            implementation(libs.kotlin.test)
        }
    }
}

// urpc KSP runs per-target (NO kspCommonMainMetadata): each target processes the commonMain
// @Urpc interfaces into its own generated source set. The generated code is commonMain-safe
// (only :urpc:protocol + coroutines Flow + serialization), so every target gets an identical copy.
dependencies {
    add("kspAndroid", libs.urpc.processor)
    add("kspJvm", libs.urpc.processor)
    add("kspWasmJs", libs.urpc.processor)
    add("kspIosArm64", libs.urpc.processor)
    add("kspIosSimulatorArm64", libs.urpc.processor)
}
```

## §2 — `:client` build → `feature/<name>/client/build.gradle.kts`

```kotlin
plugins {
    // Compose library + Paparazzi host-test wiring. Android resource processing (and the R class
    // Paparazzi resolves reflectively), the host-test component and the stub-R classpath fix all
    // come from the conventions — do not restate them here.
    id("ukpt.snapshot-testing")
    // Kotlin/JS for the Compose HTML web client; the HTML screens live in jsMain.
    id("ukpt.html-web")
    alias(libs.plugins.kotlinKsp)
}

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "feature.<name>.client"
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.<name>.api)

            // The design system. Screens read tokens and primitives from here, never literals.
            implementation(projects.platform.client.design)

            // Unified @Preview (androidx.compose.ui.tooling.preview.Preview) — multiplatform
            // since Compose 1.10, usable directly in common code. PreviewSnapshotTest discovers
            // annotated composables and snapshots them.
            implementation(compose.preview)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.enro.core)
            implementation(libs.udytils.ui)
            // urpc-client runtime for the KSP-generated service clients consumed by Repositories.
            implementation(libs.urpc.client)
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.composeViewmodel)

            implementation(libs.ktor.clientCore)
            implementation(libs.ktor.clientWebsockets)
            // NOTE: the ktor-client-cio engine is intentionally NOT in commonMain — it pulls
            // ktor-network, which imports `node:net` and breaks the wasmJs/web bundle. CIO lives
            // in the JVM/native source sets; web uses ktor-client-js (see :app:client:web).
            // Engines get wired per-platform when an HTTP/urpc client is actually added.
        }
        commonTest.dependencies {
            implementation(projects.platform.common.testing)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        getByName("androidHostTest").dependencies {
            // The snapshot harness: preview discovery, directory-grouped goldens, the
            // golden-path collision guard and the device/rendering defaults.
            implementation(libs.udytils.snapshot)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.serverCore)
            implementation(libs.ktor.serverNetty)
            implementation(libs.ktor.clientCore)
            implementation(libs.ktor.clientCio)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
        jsMain.dependencies {
            implementation(projects.platform.client.designHtml)
            implementation(libs.compose.htmlCore)
            implementation(libs.enro.html)
        }
        jsTest.dependencies {
            implementation(libs.compose.htmlTestUtils)
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", libs.enro.processor)
    add("kspAndroid", libs.enro.processor)
    add("kspJvm", libs.enro.processor)
    add("kspWasmJs", libs.enro.processor)
    add("kspIosArm64", libs.enro.processor)
    add("kspIosSimulatorArm64", libs.enro.processor)
}
```

## §3 — `:server` build → `feature/<name>/server/build.gradle.kts`

```kotlin
plugins {
    id("ukpt.jvm-library")
}

dependencies {
    api(projects.feature.<name>.api)

    // Makes the `@ArchitectureException` annotation importable so server declarations can
    // declare rule-scoped exemptions (a tiny artifact — no Konsist or test machinery).
    implementation(libs.udytils.architectureAnnotations)

    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverWebsockets)
    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    implementation(libs.ktor.clientContentNegotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.enro.common)
    implementation(libs.udytils.core)
    implementation(libs.koin.core)
    // urpc-koin provides the server binding runtime + the UrpcCall scope qualifier
    // (transitively urpc-server + urpc-protocol) for hosting @Urpc services.
    implementation(libs.urpc.koin)

    implementation(libs.ktor.server.auth)

    testImplementation(projects.platform.common.testing)
    testImplementation(libs.kotlin.test)
}
```

## §4 — `settings.gradle.kts` (add after the `:feature:core` include block)

```kotlin
include(":feature:<name>:api")
include(":feature:<name>:client")
include(":feature:<name>:server")
include(":feature:<name>:tests")
```

## §5 — Client source skeletons

`:client` → `feature/<name>/client/src/commonMain/kotlin/feature/<name>/client/ui/<Name>Destination.kt`
(the default home — move the file to `:api`, same package, only when a second feature navigates to it;
an app-shell reference never forces the move: `ClientUi.Destination.definedInApiOrClient`)
```kotlin
package feature.<name>.client.ui

import dev.enro.NavigationKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("NavigationKey.<Name>Destination")   // ProjectRules.serialNameEncodesEnclosingType: destinations pin exactly this
object <Name>Destination : NavigationKey
// Use a `@Serializable data class <Name>Destination(...) : NavigationKey` if the key has arguments.
```

`:client` → `feature/<name>/client/src/commonMain/kotlin/feature/<name>/client/ui/<Name>Screen.kt`
```kotlin
package feature.<name>.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.enro.annotations.NavigationDestination
import dev.isaacudy.udytils.state.AsyncState

@Composable
@NavigationDestination(<Name>Destination::class)
fun <Name>Screen(
    viewModel: <Name>ViewModel = viewModel(),   // ClientUi.Screen.viewModelInjection: viewModel(), NOT koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    <Name>ScreenContent(
        state = state,
        onRetry = viewModel::onRetryClicked,
        // add action + navigation callbacks here
    )
}

// ClientUi.Screen.screenContentCompanion: internal ScreenContent takes state (+ callbacks) so it renders without a ViewModel.
// ClientUi.Screen.asyncStateExhaustiveRendering: the `when` covers Idle/Loading, Error (with retry), and Success.
// Every colour, dimension and text style comes from the design system — DesignSystemRules.noLiteralsInFeatureUi
// audits for literal Color(0x…)/.dp here. Imports: platform.design.<Prefix>Theme, platform.design.<Prefix>Spacing.
@Composable
internal fun <Name>ScreenContent(
    state: <Name>State,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(<Prefix>Theme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        when (val data = state.data) {
            is AsyncState.Idle,
            is AsyncState.Loading -> {
                CircularProgressIndicator(color = <Prefix>Theme.colors.accent)
            }
            is AsyncState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(<Prefix>Spacing.md),
                ) {
                    Text(
                        text = "Something went wrong",
                        style = <Prefix>Theme.typography.title,
                        color = <Prefix>Theme.colors.onSurface,
                    )
                    <Prefix>Button(label = "Retry", onClick = onRetry)
                }
            }
            is AsyncState.Success -> {
                Text(
                    text = data.data.toString(),
                    style = <Prefix>Theme.typography.body,
                    color = <Prefix>Theme.colors.onSurface,
                )
            }
        }
    }
}

// ClientUi.Composable.screenContentPreview: every ScreenContent needs a @Preview — it becomes a
// Paparazzi snapshot via PreviewSnapshotTest. Add a @Preview per meaningful state: Loading, Error,
// populated Success, and legitimately-empty Success.
@Preview
@Composable
internal fun <Name>ScreenLoadingPreview() {
    <Prefix>PreviewFrame(colors = <Prefix>Colors.Light) {
        <Name>ScreenContent(state = <Name>State(), onRetry = {})
    }
}

@Preview
@Composable
internal fun <Name>ScreenErrorPreview() {
    <Prefix>PreviewFrame(colors = <Prefix>Colors.Light) {
        <Name>ScreenContent(
            state = <Name>State(data = AsyncState.Error(RuntimeException("Connection failed"))),
            onRetry = {},
        )
    }
}

@Preview
@Composable
internal fun <Name>ScreenSuccessPreview() {
    <Prefix>PreviewFrame(colors = <Prefix>Colors.Light) {
        <Name>ScreenContent(
            state = <Name>State(data = AsyncState.Success(/* populated domain object */)),
            onRetry = {},
        )
    }
}

@Preview
@Composable
internal fun <Name>ScreenEmptySuccessPreview() {
    <Prefix>PreviewFrame(colors = <Prefix>Colors.Light) {
        <Name>ScreenContent(
            state = <Name>State(data = AsyncState.Success(/* legitimately-empty domain object */)),
            onRetry = {},
        )
    }
}
```

`:client` → `feature/<name>/client/src/commonMain/kotlin/feature/<name>/client/ui/<Name>ViewModel.kt`
```kotlin
package feature.<name>.client.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.enro.navigationHandle
import dev.isaacudy.udytils.coroutines.JobManager
import dev.isaacudy.udytils.state.AsyncState
import dev.isaacudy.udytils.state.ViewModelState
import dev.isaacudy.udytils.state.fromFlow
import dev.isaacudy.udytils.state.viewModelState

// Inject domain interfaces; use AsyncState.fromFlow for read projections, AsyncState.fromSuspending for actions.
// ClientUi.ViewModel.usesJobManager: use dev.isaacudy.udytils.coroutines.JobManager, never `var job: Job?`.
class <Name>ViewModel(
    private val flowOf<Name>: FlowOf<Name>,
) : ViewModel() {

    private val navigation by navigationHandle<<Name>Destination>()
    private val jobManager = JobManager(viewModelScope)

    val state: ViewModelState<<Name>State> = viewModelState(<Name>State())

    init {
        loadData()
    }

    private fun loadData() {
        jobManager.launchReplacing(LOAD_DATA) {
            AsyncState.fromFlow(flowOf<Name>())
                .collect { state.update { copy(data = it) } }
        }
    }

    fun onRetryClicked() {
        loadData()
    }

    private companion object {
        const val LOAD_DATA = "loadData"
    }
}
```

`:client` → `feature/<name>/client/src/commonMain/kotlin/feature/<name>/client/ui/<Name>State.kt`
```kotlin
package feature.<name>.client.ui

import dev.isaacudy.udytils.state.AsyncState

// ClientUi.ViewModelState.usesAsyncState: use AsyncState<T> for async data and action progress.
// No sentinel defaults, no progress Booleans, no error fields.
data class <Name>State(
    val data: AsyncState</* domain projection type */> = AsyncState.Idle(),
    // val someAction: AsyncState<Unit> = AsyncState.Idle(),
)
```

`:client` → `feature/<name>/client/src/commonMain/kotlin/feature/<name>/<name>ClientDependencies.kt`
```kotlin
package feature.<name>

import feature.<name>.client.ui.<Name>ViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

// viewModelOf is mandatory: the Enro VM factory (in app/client/common/.../UkptNavigation.kt) resolves
// VMs via Koin. On wasmJs/JS there is no reflection, so a missing registration crashes at runtime
// (Factory.create … not implemented) — invisible to compileKotlinWasmJs.
// singleOf + bind registers domain interface implementations (UseCases).
// Every application class is bound by constructor reference and gives no constructor parameter a
// default (ProjectRules.constructorReferenceBindings, ProjectRules.injectableConstructorsHaveNoDefaults);
// a setting that varies between deployments is a `<Name>Config` data class bound with `single { … }`.
val <name>ClientDependencies = module {
    // singleOf(::<Name>Impl) bind <Name>::class
    // single { <Name>Config(baseUrl = "…") }
    viewModelOf(::<Name>ViewModel)
}
```

## §6 — Snapshot tests (preview-driven)
1. The harness itself is the `dev.isaacudy.udytils:snapshot` artifact — there is **nothing to copy**.
   Applying `ukpt.snapshot-testing` (§2) and depending on `libs.udytils.snapshot` in `androidHostTest`
   is the whole setup.
2. Write ONE file, `feature/<name>/client/src/androidHostTest/kotlin/platform/snapshot/PreviewSnapshotTest.kt`.
   The scanned package tree is the only per-module fact:
   ```kotlin
   package platform.snapshot

   import com.android.ide.common.rendering.api.SessionParams.RenderingMode
   import dev.isaacudy.udytils.snapshot.PreviewSnapshotCase
   import dev.isaacudy.udytils.snapshot.PreviewSnapshotTestCase
   import dev.isaacudy.udytils.snapshot.PreviewSnapshots
   import org.junit.runners.Parameterized
   import platform.testing.SnapshotTest

   @SnapshotTest
   class PreviewSnapshotTest(
       case: PreviewSnapshotCase,
   ) : PreviewSnapshotTestCase(
       case = case,
       renderingMode = RenderingMode.SHRINK,
   ) {

       companion object {
           @JvmStatic
           @Parameterized.Parameters(name = "{0}")
           fun cases(): List<PreviewSnapshotCase> = PreviewSnapshots.scan("feature.<name>")
       }
   }
   ```
   `SHRINK` crops each golden to the preview's own bounds, which is what makes the
   `<Prefix>PreviewFrame` wrapper (§5) produce a viewport-sized, screenshot-like golden instead of
   a render padded out to the harness's 960 dp square canvas. A preview without a fixed-size root
   (`fillMaxSize` against the canvas) still renders the full square, byte-identical to the old
   default — SHRINK never changes goldens for unframed previews.
   `@RunWith(Parameterized::class)` is inherited from the base class; `@Parameterized.Parameters` must stay
   on the concrete class because JUnit 4 insists on reading it there. This discovers every `@Preview` in the
   module and snapshots it (`ClientUi.Composable.previewsAreSnapshotTested` looks for a `@SnapshotTest`
   class in `androidHostTest`); the `@Preview` on `<Name>ScreenPreview` (§5) satisfies
   `ClientUi.Composable.screenContentPreview`. No per-screen test files are needed.
3. Record and verify the new module's goldens. **`--no-configuration-cache` is required** — under the
   configuration cache the R class is dropped from the test runtime classpath and every snapshot test dies with
   `ClassNotFoundException: <module>.R`:
   ```
   ./gradlew :feature:<name>:client:recordPaparazzi --no-configuration-cache
   ./gradlew :feature:<name>:client:snapshotTest --no-configuration-cache
   ```
   Goldens are grouped by the preview's declaring package and function name, e.g.
   `src/androidHostTest/snapshots/images/feature/<name>/client/ui/<Name>ScreenPreview.png`. Two previews resolving to
   the same golden path fail fast at test-parameter creation rather than silently overwriting each other.
   A framed screen preview's golden is exactly the frame — the scaffold default is 390 x 844 dp, a
   tall phone at `<Prefix>Viewport.Default`'s width.

## §7 — Subcutaneous tests → `feature/<name>/tests/`

Every ViewModel is covered by a Subcutaneous Test (`ClientUi.ViewModel.subcutaneouslyTested`), and
each of its public functions is called in one (`ClientUi.ViewModel.publicFunctionsExercised`). The
tests open the ViewModel from the production client graph with the server running in-process
against an embedded Postgres; the harness is `:platform:common:subcutaneous-server`. The
`ukpt-testing` skill has the full API. `:feature:core:tests` is the worked example.

Build file → `feature/<name>/tests/build.gradle.kts`
```kotlin
plugins {
    id("ukpt.feature-tests")
}

dependencies {
    api(projects.platform.common.subcutaneousServer)
    implementation(projects.feature.<name>.client)
    implementation(projects.feature.<name>.server)
}
```
`ModuleRules.testsModuleShape` requires the `:client` and `:server` edges. A feature without a
`:server` lists only its `:client`. In a project without a server of its own, the module depends on
`projects.platform.common.subcutaneous` instead, and the graph and scenarios follow `ukpt-testing`,
"Without a server".

Test graph → `feature/<name>/tests/src/main/kotlin/feature/<name>/tests/<Name>TestGraph.kt`
```kotlin
package feature.<name>.tests

import feature.<name>.<name>ClientDependencies
import feature.<name>.<name>ServerDependencies
import platform.testing.subcutaneous.SubcutaneousGraph
import platform.testing.subcutaneous.featureClient
import platform.testing.subcutaneous.featureServer

val <name>TestGraph = SubcutaneousGraph(
    client = featureClient(<name>ClientDependencies),
    server = featureServer(<name>ServerDependencies),
)
```
One per module, production modules only (`Testing.TestGraph.onePerTestsModule`,
`.productionModulesOnly`). A feature whose `:server` binds nothing yet passes `featureServer()`.

Scenarios → `feature/<name>/tests/src/main/kotlin/feature/<name>/tests/<Name>Scenarios.kt`
```kotlin
package feature.<name>.tests

import feature.<name>.server.domain.FlowOf<Thing>s
import feature.<name>.server.domain.Update<Thing>s
import kotlinx.coroutines.flow.first
import platform.testing.subcutaneous.Scenario

suspend fun Scenario.<thing>s(vararg names: String): List</*domain model*/> {
    val update<Thing>s = server<Update<Thing>s>()
    names.forEach { update<Thing>s.add(it) }
    return server<FlowOf<Thing>s>()().first()
}
```
A scenario writes through server domain interfaces (`Testing.Scenario.seedsThroughServerDomain`) and
returns what it created. Skip the file until the feature's server has a domain to write through.

ViewModel test → `feature/<name>/tests/src/test/kotlin/feature/<name>/tests/<Name>ViewModelTest.kt`
```kotlin
package feature.<name>.tests

import dev.isaacudy.udytils.state.getOrNull
import feature.<name>.client.ui.<Name>Destination
import feature.<name>.client.ui.<Name>ViewModel
import platform.testing.SubcutaneousTest
import platform.testing.subcutaneous.awaitMatching
import platform.testing.subcutaneous.subcutaneous
import kotlin.test.Test
import kotlin.test.assertEquals

@SubcutaneousTest
class <Name>ViewModelTest {

    @Test
    fun `an empty database loads`() = subcutaneous(<name>TestGraph) {
        val screen = viewModel<<Name>ViewModel, <Name>Destination>(<Name>Destination)

        val state = screen.viewModel.state.awaitMatching { it.data.getOrNull() != null }

        assertEquals(/* expected */, state.data.getOrNull())
    }
}
```
Run with `./gradlew :feature:<name>:tests:subcutaneousTest`. A test references only entry-point
vocabulary (ViewModels, states, destinations, domain models, `@Urpc` service interfaces) and imports
no Koin, Ktor, Exposed or `kotlinx.coroutines.test` (`Testing.SubcutaneousTest.*`).

## §8 — Dialogs (copy from `:feature:core`, don't template)

No skeleton here — the worked example is the copy-me:
`feature/core/client/src/commonMain/kotlin/feature/ukpt/client/ui/ConfirmResetDestination.kt` +
`ConfirmResetDialogScreen.kt` + `ConfirmResetViewModel.kt`, opened from
`UkptViewModel.onResetRequested()`
(`feature/core/client/src/commonMain/kotlin/feature/ukpt/client/ui/UkptViewModel.kt`). A dialog
destination follows the same screen conventions as any other: it has its own ViewModel (registered
with `viewModelOf` in the feature's Koin module — the wasm crash from a missing registration applies
here too), and the ViewModel performs the navigation actions (`complete`/`requestClose` via its
`navigationHandle`). A confirmation dialog is a plain `NavigationKey` — `complete()` means the user
confirmed, `requestClose()` means they cancelled; add `NavigationKey.WithResult<R>` only when the
dialog returns data that complete/close cannot represent. The destination declares
`directOverlayWithFade()` metadata, the opener uses `registerForNavigationResult(onCompleted = { ... })` /
`channel.open(key)` — never a `show*Dialog` flag on `State`
(`ClientUi.ViewModelState.noDialogVisibilityFlags`, `ClientUi.Composable.dialogPrimitivesOnlyInDialogDestinations`,
`ClientUi.dialogsCommunicateViaResults`).

## §9 — Wiring checklist (edits to EXISTING files — the easy-to-forget step)
- [ ] `settings.gradle.kts` — four `include(":feature:<name>:…")` (§4).
- [ ] `app/client/common/build.gradle.kts` — `commonMain` → `implementation(projects.feature.<name>.client)`.
- [ ] `app/client/common/src/commonMain/kotlin/com/isaacudy/ukpt/ClientDependencies.kt` — `import feature.<name>.<name>ClientDependencies`
      and add it to the `clientDependencies` list (`App()` installs that list; `:app:client:common:jvmTest` verifies it).
- [ ] `app/client/web-html/src/jsMain/kotlin/com/isaacudy/ukpt/ClientDependencies.kt` — the same import and list
      entry (`FeatureRules.DependencyModule.registeredInEveryClientShell` checks both lists).
- [ ] `app/client/web-html/build.gradle.kts` — `jsMain` → `implementation(projects.feature.<name>.client)`, and a
      Compose HTML screen per destination (`ukpt-html-web`). Skip both if the project deleted `app/client/web-html`.
- [ ] `app/server/build.gradle.kts` — `implementation(projects.feature.<name>.server)` (only if using the server).
- [ ] Add a navigation entry to the new `<Name>Destination` from wherever the app should open the destination.
- [ ] `app/server/src/main/kotlin/com/isaacudy/ukpt/ServerDependencies.kt` — add `<name>ServerDependencies` once
      it binds something (`:app:server:test` verifies the list). A service is the `ukpt-urpc-service` skill.
