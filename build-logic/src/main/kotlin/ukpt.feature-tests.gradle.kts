/**
 * Convention plugin for a feature's Subcutaneous Test module, `:feature:[name]:tests`.
 *
 * Applies: ukpt.jvm-base
 * Configures: kotlin.test, and one test JVM at a time with a bounded heap: each test JVM may start
 * an embedded Postgres.
 *
 * The module's build file declares the harness and the feature's modules: a feature with a server
 * declares `:platform:common:subcutaneous-server` and its `:client` and `:server`; a feature
 * without one declares `:platform:common:subcutaneous` and its `:client`.
 */
plugins {
    id("ukpt.jvm-base")
}

private val libs = versionCatalogs.named("libs")

dependencies {
    "testImplementation"(libs.findLibrary("kotlin-test").get())
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    maxHeapSize = "1g"
}
