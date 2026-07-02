plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    // Forwards @ArchitectureException to modules that depend on this one for exemptions.
    api(libs.udytils.architectureAnnotations)

    // The architecture framework (rule DSL, doc generator, JUnit harnesses); Konsist,
    // the JUnit 5 API, and kotlin.test arrive transitively via its api surface.
    testImplementation(libs.udytils.architectureCore)
    // The JUnit 5 engine that actually runs the suite.
    testImplementation(libs.junit.jupiter)
}

tasks.withType<Test> {
    useJUnitPlatform()
    outputs.upToDateWhen { false }
    // `-PupdateArchitectureDocs=true` regenerates README.md + docs/ — the IDE/CLI-friendly form of
    // the UPDATE_ARCHITECTURE_DOCS=true environment variable (which also still works).
    providers.gradleProperty("updateArchitectureDocs").orNull?.let {
        environment("UPDATE_ARCHITECTURE_DOCS", it)
    }
}
