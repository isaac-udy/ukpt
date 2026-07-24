# ukpt-feature-slice templates

Substitute `<name>` (lowercase feature/package segment) and `<Name>` (PascalCase type prefix).
These mirror `:feature:core` — if core's build files change materially, update these to match.

## §1 — `:api` build → `feature/<name>/api/build.gradle.kts`

```kotlin
plugins {
    id("ukpt.kmp-library")
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
            implementation(libs.kotlin.test)
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

    testImplementation(libs.kotlin.testJunit)
}
```

## §4 — `settings.gradle.kts` (add after the `:feature:core` include block)

```kotlin
include(":feature:<name>:api")
include(":feature:<name>:client")
include(":feature:<name>:server")
```

## §5 — Client source skeletons

`:api` → `feature/<name>/api/src/commonMain/kotlin/feature/<name>/ui/<Name>Destination.kt`
```kotlin
package feature.<name>.ui

import dev.enro.NavigationKey
import kotlinx.serialization.Serializable

@Serializable
object <Name>Destination : NavigationKey
// Use a `@Serializable data class <Name>Destination(...) : NavigationKey` if the key carries arguments.
```

`:client` → `feature/<name>/client/src/commonMain/kotlin/feature/<name>/ui/<Name>Screen.kt`
```kotlin
package feature.<name>.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.enro.annotations.NavigationDestination

@Composable
@NavigationDestination(<Name>Destination::class)
fun <Name>Screen(
    viewModel: <Name>ViewModel = viewModel(),   // UiLayer.Screen.viewModelInjection: viewModel(), NOT koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    <Name>ScreenContent(state)
}

// UiLayer.Screen.screenContentCompanion: internal ScreenContent takes state (+ callbacks) so it renders without a ViewModel.
// Every colour, dimension and text style comes from the design system — DesignSystemRules.noLiteralsInFeatureUi
// audits for literal Color(0x…)/.dp here. Imports: platform.design.<Prefix>Theme, platform.design.<Prefix>Spacing.
@Composable
internal fun <Name>ScreenContent(state: <Name>State) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(<Prefix>Theme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = state.message,
            style = <Prefix>Theme.typography.body,
            color = <Prefix>Theme.colors.onSurface,
        )
    }
}

// UiLayer.Composable.screenContentPreview: every ScreenContent needs a @Preview — it becomes a
// Paparazzi snapshot via PreviewSnapshotTest. Add a @Preview per meaningful state.
// Import: androidx.compose.ui.tooling.preview.Preview
@Preview
@Composable
internal fun <Name>ScreenPreview() {
    // Pin the palette rather than following the system, so the golden is deterministic.
    <Prefix>Theme(colors = <Prefix>Colors.Light) {
        <Name>ScreenContent(<Name>State())
    }
}
```

`:client` → `feature/<name>/client/src/commonMain/kotlin/feature/<name>/ui/<Name>ViewModel.kt`
```kotlin
package feature.<name>.ui

import androidx.lifecycle.ViewModel
import dev.enro.navigationHandle
import dev.isaacudy.udytils.state.ViewModelState
import dev.isaacudy.udytils.state.viewModelState

class <Name>ViewModel : ViewModel() {
    private val navigation by navigationHandle<<Name>Destination>()
    val state: ViewModelState<<Name>State> = viewModelState(<Name>State())
    // UiLayer.ViewModel.usesJobManager: if this VM launches coroutines, use dev.isaacudy.udytils.coroutines.JobManager, never `var job: Job?`.
}
```

`:client` → `feature/<name>/client/src/commonMain/kotlin/feature/<name>/ui/<Name>State.kt`
```kotlin
package feature.<name>.ui

data class <Name>State(
    val message: String = "Hello, <name>!",
)
```

`:client` → `feature/<name>/client/src/commonMain/kotlin/feature/<name>/<name>ClientDependencies.kt`
```kotlin
package feature.<name>

import feature.<name>.ui.<Name>ViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

// viewModelOf is mandatory: the Enro VM factory (in app/client/common/.../UkptNavigation.kt) resolves
// VMs via Koin. On wasmJs/JS there is no reflection, so a missing registration crashes at runtime
// (Factory.create … not implemented) — invisible to compileKotlinWasmJs.
val <name>ClientDependencies = module {
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

   import dev.isaacudy.udytils.snapshot.PreviewSnapshotCase
   import dev.isaacudy.udytils.snapshot.PreviewSnapshotTestCase
   import dev.isaacudy.udytils.snapshot.PreviewSnapshots
   import org.junit.runners.Parameterized

   class PreviewSnapshotTest(
       case: PreviewSnapshotCase,
   ) : PreviewSnapshotTestCase(case) {

       companion object {
           @JvmStatic
           @Parameterized.Parameters(name = "{0}")
           fun cases(): List<PreviewSnapshotCase> = PreviewSnapshots.scan("feature.<name>")
       }
   }
   ```
   `@RunWith(Parameterized::class)` is inherited from the base class; `@Parameterized.Parameters` must stay
   on the concrete class because JUnit 4 insists on reading it there. This discovers every `@Preview` in the
   module and snapshots it (`UiLayer.Composable.previewsAreSnapshotTested`, which detects the harness by
   looking for `PreviewSnapshotTestCase`); the `@Preview` on `<Name>ScreenPreview` (§5) satisfies
   `UiLayer.Composable.screenContentPreview`. No per-screen test files are needed.
3. Record and verify the new module's goldens. **`--no-configuration-cache` is required** — under the
   configuration cache the R class is dropped from the test runtime classpath and every snapshot test dies with
   `ClassNotFoundException: <module>.R`:
   ```
   ./gradlew :feature:<name>:client:recordPaparazzi --no-configuration-cache
   ./gradlew :feature:<name>:client:verifyPaparazzi --no-configuration-cache
   ```
   Goldens are grouped by the preview's declaring package and function name, e.g.
   `src/androidHostTest/snapshots/images/feature/<name>/ui/<Name>ScreenPreview.png`. Two previews resolving to
   the same golden path fail fast at test-parameter creation rather than silently overwriting each other.

## §7 — Wiring checklist (edits to EXISTING files — the easy-to-forget step)
- [ ] `settings.gradle.kts` — three `include(":feature:<name>:…")` (§4).
- [ ] `app/client/common/build.gradle.kts` — `commonMain` → `implementation(projects.feature.<name>.client)`.
- [ ] `app/client/common/src/commonMain/kotlin/com/isaacudy/ukpt/App.kt` — `import feature.<name>.<name>ClientDependencies`
      and add it to `KoinApplication(application = { modules(ukptClientDependencies, <name>ClientDependencies) })`.
- [ ] `app/server/build.gradle.kts` — `implementation(projects.feature.<name>.server)` (only if using the server).
- [ ] Add a navigation entry to the new `<Name>Destination` from wherever the app should reach it.
- [ ] (Server DI / urpc host: defer to the `ukpt-urpc-service` skill — done when the feature gets its first service.)
