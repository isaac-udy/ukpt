/**
 * Mutation testing: `./gradlew runMutants`.
 *
 * Applied by: the root build, after ukpt.test-coverage.
 * Configures: the udytils mutation plugin with the coverage report's package scope, the
 * architecture `classification.json`, and the `testCoverage { }` module expectations. The catalog
 * rules are in the root build's `mutation { }` block.
 */

import ukpt.coverage.TestCoverageExtension

apply(plugin = "dev.isaacudy.udytils.mutation")

private val testCoverage = the<TestCoverageExtension>()
private val architecture = ":platform:common:architecture"

extensions.getByName("mutation").withGroovyBuilder {
    getProperty("packages").withGroovyBuilder { "set"(testCoverage.instrumentedPackages) }
    getProperty("moduleExpectations").withGroovyBuilder { "set"(testCoverage.moduleExpectations) }
    getProperty("classification").withGroovyBuilder {
        "set"(project(architecture).layout.buildDirectory.file("reports/architecture/classification.json"))
    }
}

tasks.named("runMutants") {
    dependsOn("$architecture:classifyArchitecture")
}
