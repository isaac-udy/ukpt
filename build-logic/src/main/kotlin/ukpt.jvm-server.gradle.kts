/**
 * Convention plugin for JVM server application modules (e.g. :app:server).
 *
 * Applies: ukpt.jvm-base, KotlinSerialization
 *
 * Executable server applications should apply the Ktor plugin directly, set
 * `application.mainClass`, and add their own dependencies.
 */
plugins {
    id("ukpt.jvm-base")
    id("org.jetbrains.kotlin.plugin.serialization")
}
