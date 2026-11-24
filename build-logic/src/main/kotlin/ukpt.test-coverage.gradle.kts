/**
 * The test coverage report: `./gradlew testCoverageReport --no-configuration-cache`.
 *
 * Applied by: the root build.
 * Configures: Kover over the `:feature`, `:platform` and `:app` modules. Kover instruments the test
 * category tasks only when the build runs `testCoverageReport` or `collectTestCoverage`; every other
 * build runs uninstrumented tests. Each category task writes its own coverage, so the report shows
 * which category ran each line, and counts a line as covered only when a category that the
 * declaration's Construct expects ran the line. End-to-end Tests are not instrumented.
 *
 * Paparazzi requires `--no-configuration-cache`: the Snapshot Tests run in the coverage build.
 */

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import ukpt.coverage.CollectTestCoverageTask
import ukpt.coverage.TestCoverageExtension
import ukpt.coverage.TestCoverageReportTask

plugins {
    id("org.jetbrains.kotlinx.kover")
}

private val coverageTaskNames = setOf("testCoverageReport", "collectTestCoverage")
private val coverageRun = gradle.startParameter.taskNames.any { it.substringAfterLast(':') in coverageTaskNames }

private val categoryTasks = mapOf(
    "subcutaneousJvmTest" to "subcutaneous",
    "unitTest" to "unit",
    "componentTest" to "component",
    "wiringTest" to "wiring",
    "adapterTest" to "adapter",
    "checkTest" to "check",
)

// The architecture module's tests check rule fixtures, not production code.
private fun Project.isReported() =
    listOf(":feature:", ":platform:", ":app:").any { path.startsWith(it) } && path != ":platform:common:architecture"

private val reported = subprojects.filter { it.isReported() }

// The Android host test task runs the Snapshot Tests in a module with Paparazzi, and the Subcutaneous
// Tests in a `:tests` module whose hosts omit the JVM.
private fun Project.androidHostCategory(): String? = when {
    pluginManager.hasPlugin("app.cash.paparazzi") -> "snapshot"
    path.startsWith(":feature:") && path.endsWith(":tests") &&
        "jvm" !in (findProperty("ukpt.subcutaneousHosts") as String? ?: "jvm,android").split(',') -> "subcutaneous"
    else -> null
}

private fun Project.instrumentedTasks(): Map<String, String> =
    categoryTasks + listOfNotNull(androidHostCategory()?.let { "testAndroidHostTest" to it })

val testCoverage = extensions.create<TestCoverageExtension>("testCoverage")

configure<KoverProjectExtension> {
    merge {
        subprojects { it.isReported() }
        instrumentation {
            if (!coverageRun) {
                disabledForAll.set(true)
            } else {
                disabledForTestTasks.addAll(
                    listOf("test", "jvmTest", "testAndroidHostTest", "endToEndTest", "subcutaneousAndroidHostTest", "jvmDevTest")
                        .filter { it !in project.instrumentedTasks() },
                )
            }
        }
    }
}

val collectTestCoverage = tasks.register<CollectTestCoverageTask>("collectTestCoverage") {
    group = "verification"
    description = "Runs the test categories with coverage and writes one Kover XML report per category."
    repositoryDirectory.set(layout.projectDirectory)
    outputDirectory.set(layout.buildDirectory.dir("test-coverage"))
    excludedClasses.addAll("feature.*.tests.*", "enro_generated_bindings.*", "*.generated.resources.*")
    modules.putAll(provider { reported.associate { it.path to it.projectDir.relativeTo(rootDir).invariantSeparatorsPath } })
    taskCategories.putAll(provider {
        reported.flatMap { p -> p.instrumentedTasks().map { (task, category) -> "${p.path}|$task" to category } }.toMap()
    })
    reported.forEach { p ->
        artifacts.from(p.layout.buildDirectory.dir("kover").map { dir -> dir.asFileTree.matching { include("*.artifact") } })
        dependsOn(p.tasks.matching { it.name.startsWith("koverGenerateArtifact") })
        dependsOn(p.tasks.matching { it.name in p.instrumentedTasks() })
    }
}

tasks.register<TestCoverageReportTask>("testCoverageReport") {
    group = "verification"
    description = "Writes the test coverage report to build/reports/test-coverage (HTML, Markdown and JSON)."
    dependsOn(collectTestCoverage, ":platform:common:architecture:classifyArchitecture")
    manifest.set(layout.buildDirectory.file("test-coverage/manifest.json"))
    classification.set(project(":platform:common:architecture").layout.buildDirectory.file("reports/architecture/classification.json"))
    moduleExpectations.set(testCoverage.moduleExpectations)
    categoryNames.putAll(
        mapOf(
            "subcutaneous" to "Subcutaneous", "unit" to "Unit", "component" to "Component", "snapshot" to "Snapshot",
            "wiring" to "Wiring", "adapter" to "Adapter", "check" to "Check", "end-to-end" to "End-to-end",
        ),
    )
    repositoryDirectory.set(layout.projectDirectory)
    outputDirectory.set(layout.buildDirectory.dir("reports/test-coverage"))
}

// Renders the report from the last collectTestCoverage run without running the tests again.
tasks.register<TestCoverageReportTask>("renderTestCoverageReport") {
    val report = tasks.named<TestCoverageReportTask>("testCoverageReport").get()
    manifest.set(report.manifest)
    classification.set(report.classification)
    moduleExpectations.set(report.moduleExpectations)
    categoryNames.set(report.categoryNames)
    repositoryDirectory.set(report.repositoryDirectory)
    outputDirectory.set(report.outputDirectory)
}
