/**
 * Convention plugin for JVM server application modules (e.g. :app:server).
 *
 * Applies: KotlinJvm, Ktor
 * Configures: Common Kotlin compiler options.
 *
 * Consuming modules should set `application.mainClass` and add their own dependencies.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
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
