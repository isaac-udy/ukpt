import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import ukpt.ArchiveNaming

/**
 * Convention plugin for a feature's Subcutaneous Test module, `:feature:[name]:tests`.
 *
 * Applies: KotlinMultiplatform + the AGP 9 Android KMP library plugin.
 * Configures: the Android target, a `jvm()` target when the `ukpt.subcutaneousHosts` Gradle property
 * lists `jvm`, kotlin.test on JUnit 5, and one task per host in that property (`jvm`, `android`;
 * default `jvm,android`) that runs the module's Subcutaneous Tests on that host:
 * `subcutaneousJvmTest`, `subcutaneousAndroidHostTest`.
 * Each host runs one test JVM at a time with a bounded heap: each test JVM may start an embedded
 * Postgres.
 *
 * The test graph, scenarios, fakes and robots live in `src/commonMain`, and the tests in
 * `src/commonTest`. The module's build file declares the harness and the feature's
 * modules in `commonMain`: a feature with a server declares `:platform:common:subcutaneous-server`
 * and its `:client` and `:server`; a feature without one declares `:platform:common:subcutaneous`
 * and its `:client`.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

private val libs = versionCatalogs.named("libs")
private val supportedHosts = setOf("jvm", "android")
private val hosts = providers.gradleProperty("ukpt.subcutaneousHosts").getOrElse("jvm,android")
    .split(',').map(String::trim).filter(String::isNotEmpty).toSet()

require(hosts.isNotEmpty() && supportedHosts.containsAll(hosts)) {
    "ukpt.subcutaneousHosts lists ${hosts.ifEmpty { setOf("no host") }}. Set it to a comma-separated list of: ${supportedHosts.joinToString()}."
}

base {
    archivesName.set(ArchiveNaming.baseNameFor(project.path))
}

kotlin {
    if ("jvm" in hosts) {
        jvm {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
            }
        }
    }

    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = project.path.removePrefix(":").replace(':', '.').replace('-', '_')
        compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
        minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        if ("android" in hosts) withHostTestBuilder {}
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.findLibrary("kotlin-test").get())
        }
    }
}

private val hostTestTasks = mapOf(
    "jvm" to ("jvmTest" to "subcutaneousJvmTest"),
    "android" to ("testAndroidHostTest" to "subcutaneousAndroidHostTest"),
).filterKeys { it in hosts }

hostTestTasks.keys.forEach { host ->
    val runtimeOnly = if (host == "jvm") "jvmTestRuntimeOnly" else "androidHostTestRuntimeOnly"
    dependencies {
        add(runtimeOnly, platform(libs.findLibrary("junit-bom").get()))
        add(runtimeOnly, libs.findLibrary("junit-platformLauncher").get())
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxParallelForks = 1
    maxHeapSize = "1g"
}

afterEvaluate {
    hostTestTasks.forEach { (host, names) ->
        val (baseName, taskName) = names
        val base = tasks.named<Test>(baseName)
        tasks.register<Test>(taskName) {
            group = "verification"
            description = "Runs the Subcutaneous Tests on the $host host."
            testClassesDirs = base.get().testClassesDirs
            classpath = base.get().classpath
            systemProperties(base.get().systemProperties)
            jvmArgs(base.get().jvmArgs.orEmpty())
            useJUnitPlatform { includeTags("subcutaneous") }
        }
    }
}
