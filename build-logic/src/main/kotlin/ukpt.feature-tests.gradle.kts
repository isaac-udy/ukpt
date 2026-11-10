/**
 * Convention plugin for a feature's Subcutaneous Test module, `:feature:[name]:tests`.
 *
 * Applies: ukpt.jvm-base
 * Configures: kotlin.test, the udytils htmx `ElementId`s the robots locate by, and one test JVM at a
 * time with a bounded heap: each test JVM starts an embedded Postgres.
 *
 * The module's build file declares the harness (`:platform:common:subcutaneous`) and the feature's
 * `:server`.
 */
plugins {
    id("ukpt.jvm-base")
}

private val libs = versionCatalogs.named("libs")

dependencies {
    "implementation"(libs.findLibrary("udytils-htmx").get())
    "testImplementation"(libs.findLibrary("kotlin-test").get())
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    maxHeapSize = "1g"
}
