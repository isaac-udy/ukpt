import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import ukpt.ArchiveNaming

/**
 * Convention plugin for a feature's Subcutaneous Test module, `:feature:[name]:tests`.
 *
 * Applies: KotlinMultiplatform + the AGP 9 Android KMP library plugin, ukpt.subcutaneous-web-tests.
 * Configures: the Android target, one target per other host the `ukpt.subcutaneousHosts` Gradle
 * property lists, kotlin.test on JUnit 5, and one task per listed host that runs the module's
 * Subcutaneous Tests on that host:
 *
 * | Host      | Target                 | Task                                |
 * |-----------|------------------------|-------------------------------------|
 * | `jvm`     | `jvm()`                | `subcutaneousJvmTest`               |
 * | `android` | `androidLibrary`       | `subcutaneousAndroidHostTest`       |
 * | `ios`     | `iosSimulatorArm64()`  | `subcutaneousIosSimulatorArm64Test` |
 * | `wasmJs`  | `wasmJs { browser() }` | `subcutaneousWasmJsTest`            |
 * | `js`      | `js { browser() }`     | `subcutaneousJsTest`                |
 *
 * The default is `jvm,android`. The root `gradle.properties` sets the list for every `:tests`
 * module; a `gradle.properties` in a `:tests` module's directory overrides the list for that
 * module. A module whose graph has a server depends on JVM-only modules, so the module fails
 * configuration when its list contains `ios`, `wasmJs` or `js`. Each JVM host runs one test JVM at
 * a time with a bounded heap: each test JVM may start an embedded Postgres.
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
    id("ukpt.subcutaneous-web-tests")
}

private val libs = versionCatalogs.named("libs")
private val supportedHosts = setOf("jvm", "android", "ios", "wasmJs", "js")
private val hosts = (findProperty("ukpt.subcutaneousHosts") as String? ?: "jvm,android")
    .split(',').map(String::trim).filter(String::isNotEmpty).toSet()

require(hosts.isNotEmpty() && supportedHosts.containsAll(hosts)) {
    "ukpt.subcutaneousHosts lists ${hosts.ifEmpty { setOf("no host") }}. Set it to a comma-separated list of: ${supportedHosts.joinToString()}."
}

// The Compose plugin copies Skiko's runtime into the web test bundles, which a Compose client requires.
if ("wasmJs" in hosts || "js" in hosts) {
    pluginManager.apply("org.jetbrains.compose")
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
}

// The check fails every web test compilation that depends on Skiko; it guards runComposeUiTest only
// (CMP-4906), which no Subcutaneous Test calls. ukpt.compose-library disables it for the same reason.
tasks.named { it.startsWith("checkComposeUiTestConfigurationFor") }.configureEach {
    enabled = false
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

    if ("ios" in hosts) iosSimulatorArm64()

    if ("wasmJs" in hosts) {
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs { browser() }
    }

    if ("js" in hosts) js { browser() }

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

private val otherHostTestTasks = mapOf(
    "ios" to ("iosSimulatorArm64Test" to "subcutaneousIosSimulatorArm64Test"),
    "wasmJs" to ("wasmJsBrowserTest" to "subcutaneousWasmJsTest"),
    "js" to ("jsBrowserTest" to "subcutaneousJsTest"),
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
    val serverModules = listOf("commonMainApi", "commonMainImplementation")
        .flatMap { configurations.getByName(it).dependencies.withType<ProjectDependency>() }
        .map { it.path }
        .filter { it == ":platform:common:subcutaneous-server" || it.endsWith(":server") }
    require(serverModules.isEmpty() || otherHostTestTasks.isEmpty()) {
        "$path depends on ${serverModules.joinToString()}, which run on the JVM only, and the module's ukpt.subcutaneousHosts lists ${otherHostTestTasks.keys.joinToString()}. Remove ${otherHostTestTasks.keys.joinToString()} from ukpt.subcutaneousHosts, or fake the backend at the client edge and remove the server dependencies."
    }

    otherHostTestTasks.forEach { (host, names) ->
        val (baseName, taskName) = names
        tasks.register(taskName) {
            group = "verification"
            description = "Runs the Subcutaneous Tests on the $host host."
            dependsOn(baseName)
        }
    }

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
