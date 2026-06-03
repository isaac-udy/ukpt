import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Convention plugin for Kotlin Multiplatform library modules.
 *
 * Applies: KotlinMultiplatform + the AGP 9 Android KMP library plugin
 * (`com.android.kotlin.multiplatform.library`).
 * Configures: All KMP targets (Android, iOS, JVM, WasmJS), compiler options, Android defaults.
 *
 * The Android target is configured via the `kotlin { androidLibrary { } }` accessor. We use
 * `androidLibrary` (not the `android` alias) because that name is what resolves on AGP 9.0 —
 * the latest AGP supported by IntelliJ. Both names refer to the same target on AGP 9.1+.
 *
 * Consuming modules set their Android `namespace` via `kotlin { androidLibrary { namespace = ... } }`.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

private val libs = versionCatalogs.named("libs")

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        compileSdk = 36
        minSdk = 24
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xexplicit-backing-fields",
            "-Xexpect-actual-classes",
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.findLibrary("kotlinx-serialization").get())
        }
    }
}
