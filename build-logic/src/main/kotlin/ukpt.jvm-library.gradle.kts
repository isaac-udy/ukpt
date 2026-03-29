/**
 * Convention plugin for JVM-only library/server modules.
 *
 * Applies: KotlinJvm
 * Configures: Common Kotlin compiler options.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xexplicit-backing-fields",
            "-Xexpect-actual-classes",
        )
    }
}
