import gradle.kotlin.dsl.accessors._c8c1f5955a7e55b45b290b15209525cf.versionCatalogs

/**
 * Convention plugin for JVM-only library/server modules.
 *
 * Applies: KotlinJvm
 * Configures: Common Kotlin compiler options.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

private val libs = versionCatalogs.named("libs")

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xexplicit-backing-fields",
            "-Xexpect-actual-classes",
        )
    }
}

dependencies {
    implementation(libs.findLibrary("kotlinx-serialization").get())
}
