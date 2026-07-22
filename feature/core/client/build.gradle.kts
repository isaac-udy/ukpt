plugins {
    // Compose library + Paparazzi host-test wiring (see ukpt.snapshot-testing).
    id("ukpt.snapshot-testing")
    alias(libs.plugins.kotlinKsp)
}

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "feature.core.client"
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.core.api)

            // The design system. Features read tokens and primitives from here, never literals.
            implementation(projects.platform.client.ui)

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
            // golden-path collision guard and the device/rendering defaults. Brings
            // ComposablePreviewScanner + JUnit4 with it; Paparazzi itself comes from the plugin.
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
