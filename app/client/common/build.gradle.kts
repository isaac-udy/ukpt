plugins {
    id("ukpt.compose-library")
    alias(libs.plugins.kotlinKsp)
}

private val projectNamespace = providers.gradleProperty("ukpt.projectNamespace").get()

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        // Distinct from the application module's namespace (AGP 9 requires unique namespaces).
        namespace = "$projectNamespace.common"
    }

    // iOS framework consumed by the iOS application (an Xcode project consuming `App.framework`).
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "App"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.core.client)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // Exposed as `api` because this module's public API surface includes Enro types
            // (the @NavigationComponent object and the generated installNavigationController).
            api(libs.enro.core)
            implementation(libs.udytils.ui)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.composeViewmodel)

            implementation(libs.ktor.clientCore)
            implementation(libs.ktor.clientWebsockets)
            // ktor-client-cio is JVM/native-only (it pulls ktor-network -> `node:net`, which
            // breaks the wasmJs/web bundle). Web uses ktor-client-js; other engines are wired
            // per-platform when an HTTP/urpc client is actually added.
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
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
