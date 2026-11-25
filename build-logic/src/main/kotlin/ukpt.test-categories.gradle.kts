/**
 * Test categories as Gradle tasks.
 *
 * Applied by: ukpt.jvm-base, ukpt.kmp-library.
 * Configures: the module's JVM test task (`test`, or `jvmTest` for multiplatform) on JUnit 5, and
 * one task per category that runs only the JVM test classes carrying that category's annotation
 * from `:platform:common:testing`. Gradle runs a task name given without a path in every module
 * that has it, so `./gradlew unitTest` runs the unit tests of the whole build.
 *
 * `testAndroidHostTest` runs on JUnit 5 with two engines: Jupiter runs the kotlin.test classes and
 * Vintage runs Paparazzi's JUnit 4 classes. A module with no JVM test task builds its category
 * tasks from `testAndroidHostTest`, restricted to the Jupiter engine. A module with a JVM test
 * task runs its category tasks on the JVM only.
 *
 * Subcutaneous Tests have no category task: ukpt.feature-tests registers one task per host.
 *
 * kotlin.test has no tags on JS, wasm or iOS. `componentTest` therefore also runs `jsTest` in a
 * module that has `src/jsTest`, which includes the module's commonTest classes compiled to JS.
 */

import ukpt.IndexTestCategoryTags
import ukpt.runAndroidHostTestsOnJUnitPlatform

private val libs = versionCatalogs.named("libs")

private val categoryTags = mapOf(
    "unitTest" to "unit",
    "componentTest" to "component",
    "snapshotTest" to "snapshot",
    "wiringTest" to "wiring",
    "adapterTest" to "adapter",
    "checkTest" to "check",
    "endToEndTest" to "end-to-end",
)

afterEvaluate {
    val hasAndroidHostTest = tasks.names.contains("testAndroidHostTest")
    runAndroidHostTestsOnJUnitPlatform()

    val (baseName, runtimeOnly) = when {
        tasks.names.contains("jvmTest") -> "jvmTest" to "jvmTestRuntimeOnly"
        tasks.names.contains("test") && configurations.names.contains("testRuntimeOnly") -> "test" to "testRuntimeOnly"
        hasAndroidHostTest -> "testAndroidHostTest" to null
        else -> return@afterEvaluate
    }
    if (runtimeOnly != null) {
        dependencies {
            add(runtimeOnly, platform(libs.findLibrary("junit-bom").get()))
            add(runtimeOnly, libs.findLibrary("junit-platformLauncher").get())
        }
    }
    val base = tasks.named<Test>(baseName) { useJUnitPlatform() }
    val tagIndex = tasks.register<IndexTestCategoryTags>("indexTestCategoryTags") {
        testClasses.from(base.get().testClassesDirs.asFileTree.matching { include("**/*.class") })
        runtimeClasspath.from(base.get().classpath)
        jupiterOnly.set(baseName == "testAndroidHostTest")
        indexFile.set(layout.buildDirectory.file("test-categories/tags.txt"))
    }
    val explicitTestFilter = gradle.startParameter.taskNames.any { it == "--tests" || it.startsWith("--tests=") }
    val hasJsTests = tasks.names.contains("jsTest") && file("src/jsTest").exists()
    val type = when (baseName) {
        "jvmTest" -> ukpt.JvmCategoryTest::class.java
        "testAndroidHostTest" -> ukpt.AndroidCategoryTest::class.java
        else -> Test::class.java
    }
    categoryTags.forEach { (taskName, tag) ->
        tasks.register(taskName, type) {
            group = "verification"
            description = "Runs the JVM test classes annotated as $tag tests."
            val classes = base.get().testClassesDirs
            testClassesDirs = if (explicitTestFilter) {
                classes
            } else {
                files(tagIndex.flatMap { it.indexFile }.map { index ->
                    val tags = index.asFile.takeIf { it.isFile }?.readLines().orEmpty()
                    if (tag in tags || "*" in tags) classes.files else emptySet()
                })
            }
            classpath = base.get().classpath
            systemProperties(base.get().systemProperties)
            useJUnitPlatform {
                if (baseName == "testAndroidHostTest") includeEngines("junit-jupiter")
                includeTags(tag)
            }
            failOnNoDiscoveredTests = false
            if (taskName == "componentTest" && hasJsTests) dependsOn("jsTest")
        }
    }
}
