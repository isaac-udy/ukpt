import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("ukpt.compose-application")
    alias(libs.plugins.kotlinKsp)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
        }
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

            implementation(libs.enro.core)
            implementation(libs.udytils.ui)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.composeViewmodel)

            implementation(libs.ktor.clientCore)
            implementation(libs.ktor.clientCio)
            implementation(libs.ktor.clientWebsockets)
            implementation(libs.kotlinx.rpc.krpcClient)
            implementation(libs.kotlinx.rpc.krpcKtorClient)
            implementation(libs.kotlinx.rpc.krpcSerializationJson)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.clientJs)
            implementation(libs.kotlinx.browser)
        }
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
    add("kspCommonMainMetadata", libs.enro.processor)
    add("kspAndroid", libs.enro.processor)
    add("kspJvm", libs.enro.processor)
    add("kspWasmJs", libs.enro.processor)
    add("kspIosX64", libs.enro.processor)
    add("kspIosArm64", libs.enro.processor)
    add("kspIosSimulatorArm64", libs.enro.processor)
}

private val projectNamespace = providers.gradleProperty("ukpt.projectNamespace").get()

android {
    namespace = projectNamespace
    defaultConfig {
        applicationId = projectNamespace
    }
}

compose.desktop {
    application {
        mainClass = "$projectNamespace.MainKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Deb,
            )
            packageName = projectNamespace
            packageVersion = "1.0.0"
        }
    }
}
