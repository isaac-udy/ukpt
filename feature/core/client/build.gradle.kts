plugins {
    // Compose library + Paparazzi host-test wiring (see ukpt.snapshot-testing).
    id("ukpt.snapshot-testing")
    // Kotlin/JS for the Compose HTML web client; the HTML screens live in jsMain.
    id("ukpt.html-web")
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
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.composeViewmodel)
        }
        commonTest.dependencies {
            implementation(projects.platform.common.testing)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        getByName("androidHostTest").dependencies {
            // The snapshot harness: preview discovery, directory-grouped goldens, the
            // golden-path collision guard and the device/rendering defaults. Brings
            // ComposablePreviewScanner + JUnit4 with it; Paparazzi itself comes from the plugin.
            implementation(libs.udytils.snapshot)
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
