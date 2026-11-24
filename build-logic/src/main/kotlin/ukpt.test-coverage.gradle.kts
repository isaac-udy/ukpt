/**
 * The test coverage report: `./gradlew testCoverageReport`.
 *
 * Applied by: the root build.
 * Configures: Kover over the `:feature`, `:platform` and `:app` modules. Kover instruments the test
 * category tasks only when the build runs `testCoverageReport` or `collectTestCoverage`; every other
 * build runs uninstrumented tests. Each category task writes its own coverage, so the report shows
 * which category ran each line, and counts a line as covered only when a category that the
 * declaration's Construct expects ran the line. End-to-end Tests are not instrumented.
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
    "subcutaneousAndroidHostTest" to "subcutaneous",
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

// In a coverage run, the Android host test task of a module with Paparazzi runs the Snapshot Tests only.
private fun Project.instrumentedTasks(): Map<String, String> =
    categoryTasks + if (pluginManager.hasPlugin("app.cash.paparazzi")) mapOf("testAndroidHostTest" to "snapshot") else emptyMap()

if (coverageRun) {
    reported.forEach { p ->
        p.pluginManager.withPlugin("app.cash.paparazzi") {
            p.tasks.withType<Test>().named { it == "testAndroidHostTest" }.configureEach {
                useJUnitPlatform { includeEngines("junit-vintage") }
            }
        }
    }
}

val testCoverage = extensions.create<TestCoverageExtension>("testCoverage").apply {
    instrumentedPackages("feature.*", "platform.*", "app.*", "${providers.gradleProperty("ukpt.projectNamespace").get()}.*")
}

configure<KoverProjectExtension> {
    merge {
        subprojects { it.isReported() }
        instrumentation {
            if (!coverageRun) {
                disabledForAll.set(true)
            } else {
                includedClasses.addAll(testCoverage.instrumentedPackages)
                disabledForTestTasks.addAll(
                    listOf("test", "jvmTest", "testAndroidHostTest", "endToEndTest", "jvmDevTest")
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
    instrumentedPackages.set(testCoverage.instrumentedPackages)
    excludedClasses.addAll("feature.*.tests.*", "enro_generated_bindings.*", "*.generated.resources.*")
    modules.putAll(provider { reported.associate { it.path to it.projectDir.relativeTo(rootDir).invariantSeparatorsPath } })
    taskCategories.putAll(provider {
        reported.flatMap { p -> p.instrumentedTasks().map { (task, category) -> "${p.path}|$task" to category } }.toMap()
    })
    testResultDirectories.putAll(provider {
        reported.flatMap { p ->
            p.tasks.withType<Test>().filter { it.name in p.instrumentedTasks() }
                .map { "${p.path}|${it.name}" to it.reports.junitXml.outputLocation.get().asFile.absolutePath }
        }.toMap()
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
