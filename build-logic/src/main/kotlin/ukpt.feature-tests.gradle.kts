/**
 * Convention plugin for a feature's Subcutaneous Test module, `:feature:[name]:tests`.
 *
 * Applies: ukpt.jvm-base
 * Configures: kotlin.test, the udytils htmx `ElementId`s the robots locate by, one test JVM at a
 * time with a bounded heap (each test JVM starts an embedded Postgres), and `subcutaneousJvmTest`,
 * which runs the module's Subcutaneous Tests. The JVM is the only host: the in-process server and
 * embedded Postgres run on the JVM.
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

afterEvaluate {
    val base = tasks.named<Test>("test")
    tasks.register<Test>("subcutaneousJvmTest") {
        group = "verification"
        description = "Runs the Subcutaneous Tests on the jvm host."
        testClassesDirs = base.get().testClassesDirs
        classpath = base.get().classpath
        systemProperties(base.get().systemProperties)
        jvmArgs(base.get().jvmArgs.orEmpty())
        useJUnitPlatform { includeTags("subcutaneous") }
    }
}
