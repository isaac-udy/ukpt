/**
 * Test categories as Gradle tasks.
 *
 * Applied by: ukpt.jvm-base, ukpt.kmp-library.
 * Configures: the module's JVM test task (`test`, or `jvmTest` for multiplatform) on JUnit 5, and
 * one task per category that runs only the JVM test classes carrying that category's annotation
 * from `:platform:common:testing`. Gradle runs a task name given without a path in every module
 * that has it, so `./gradlew unitTest` runs the unit tests of the whole build.
 *
 * kotlin.test has no tags on JS, wasm or iOS. `componentTest` therefore also runs `jsTest` in a
 * module that has `src/jsTest`, which includes the module's commonTest classes compiled to JS.
 * `snapshotTest` (from ukpt.snapshot-testing-base) runs Paparazzi, which uses JUnit 4.
 */

private val libs = versionCatalogs.named("libs")

private val categoryTags = mapOf(
    "unitTest" to "unit",
    "subcutaneousTest" to "subcutaneous",
    "componentTest" to "component",
    "wiringTest" to "wiring",
    "adapterTest" to "adapter",
    "checkTest" to "check",
    "endToEndTest" to "end-to-end",
)

afterEvaluate {
    val (baseName, runtimeOnly) = when {
        tasks.names.contains("jvmTest") -> "jvmTest" to "jvmTestRuntimeOnly"
        tasks.names.contains("test") && configurations.names.contains("testRuntimeOnly") -> "test" to "testRuntimeOnly"
        else -> return@afterEvaluate
    }
    dependencies {
        add(runtimeOnly, platform(libs.findLibrary("junit-bom").get()))
        add(runtimeOnly, libs.findLibrary("junit-platformLauncher").get())
    }
    val base = tasks.named<Test>(baseName) { useJUnitPlatform() }
    val hasJsTests = tasks.names.contains("jsTest") && file("src/jsTest").exists()
    categoryTags.forEach { (taskName, tag) ->
        tasks.register<Test>(taskName) {
            group = "verification"
            description = "Runs the JVM test classes annotated as $tag tests."
            testClassesDirs = base.get().testClassesDirs
            classpath = base.get().classpath
            systemProperties(base.get().systemProperties)
            useJUnitPlatform { includeTags(tag) }
            failOnNoDiscoveredTests = false
            if (taskName == "componentTest" && hasJsTests) dependsOn("jsTest")
        }
    }
}
