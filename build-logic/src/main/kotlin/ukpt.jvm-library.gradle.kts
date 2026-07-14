/**
 * Convention plugin for JVM-only library/server modules.
 *
 * Applies: ukpt.jvm-base, KotlinSerialization
 * Configures: Kotlin serialization runtime.
 */
plugins {
    id("ukpt.jvm-base")
    id("org.jetbrains.kotlin.plugin.serialization")
}

private val libs = versionCatalogs.named("libs")

dependencies {
    implementation(libs.findLibrary("kotlinx-serialization").get())
}
