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
 * The Android target is configured via the `kotlin { androidLibrary { } }` accessor. Both
 * submodules use the same name, and it resolves on every AGP 9.x (the `android` alias only
 * exists from 9.1), so the three composite builds share one spelling.
 *
 * Consuming modules set their Android `namespace` via `kotlin { androidLibrary { namespace = ... } }`.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

private val libs = versionCatalogs.named("libs")
private val androidCompileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
private val androidMinSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        compileSdk = androidCompileSdk
        minSdk = androidMinSdk
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm {
        // Must be the DSL-level pin: a tasks.withType<KotlinJvmCompile> override changes the
        // bytecode but not the target's published metadata, leaving ukpt.jvm-base-pinned consumers
        // unable to consume (or inline from) a jvm() variant floated to the ambient JDK.
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.findLibrary("kotlinx-serialization").get())
        }
    }
}
