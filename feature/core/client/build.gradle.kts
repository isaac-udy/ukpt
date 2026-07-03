plugins {
    id("ukpt.compose-library")
    alias(libs.plugins.kotlinKsp)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.paparazzi)
}

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "feature.core.client"
        // Generate the R class + process Android resources so Paparazzi host tests can resolve
        // R classes at runtime (otherwise ClassNotFoundException: feature.core.client.R).
        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
        // Host (JVM) unit-test component — Paparazzi attaches its record/verify tasks here and
        // reads the KMP `androidHostTest` source set.
        withHostTestBuilder {
        }.configure {
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.core.api)

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
            // Discovers @Preview composables on the test classpath and drives Paparazzi from them.
            implementation(libs.composablePreviewScanner.android)
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

// Paparazzi loads R classes (this module's + every dependency's: dev.enro.R, androidx.*.R, …)
// reflectively at runtime. The KMP library plugin puts the aggregated host-test stub R jar on the
// *compile* classpath only, so add it to the test runtime classpath via doFirst (which wins over
// AGP's lazily-provided AndroidUnitTest classpath); otherwise the tests fail with
// ClassNotFoundException. Adding it to androidHostTestRuntimeOnly would create a cycle, since the
// stub-R task consumes the runtime classpath as input.
tasks.withType<Test>().configureEach {
    if (name == "testAndroidHostTest") {
        dependsOn("generateAndroidHostTestStubRFile")
        val rJar = files(
            layout.buildDirectory.file("intermediates/compile_and_runtime_r_class_jar/androidHostTest/generateAndroidHostTestStubRFile/R.jar")
        )
        doFirst {
            classpath = classpath + rJar
        }
    }
}
