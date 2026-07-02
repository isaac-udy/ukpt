plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    testImplementation(libs.kotlin.testJunit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.konsist)
    testImplementation(libs.kotlin.reflect)
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
