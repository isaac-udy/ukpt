plugins {
    id("ukpt.kmp-library")
    alias(libs.plugins.kotlinKsp)
}

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "feature.core.api"
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

// urpc KSP runs per-target (no kspCommonMainMetadata): each target processes the commonMain
// @Urpc interfaces into its own generated source set. The generated code is commonMain-safe
// (only :urpc:protocol + coroutines Flow + serialization), so every target gets an identical copy.
dependencies {
    add("kspAndroid", libs.urpc.processor)
    add("kspJvm", libs.urpc.processor)
    add("kspWasmJs", libs.urpc.processor)
    add("kspIosArm64", libs.urpc.processor)
    add("kspIosSimulatorArm64", libs.urpc.processor)
}
