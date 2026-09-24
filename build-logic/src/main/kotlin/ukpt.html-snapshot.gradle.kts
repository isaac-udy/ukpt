// HTML golden snapshots (dev.isaacudy.udytils:html-snapshot) for a module's tests. Goldens live in
// src/test/snapshots/html; `-PrecordHtmlSnapshots` rewrites them instead of comparing.

val recordHtmlSnapshots = providers.gradleProperty("recordHtmlSnapshots").isPresent

tasks.withType<Test>().configureEach {
    inputs.files(layout.projectDirectory.dir("src/test/snapshots/html").asFileTree)
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("htmlSnapshots")
    systemProperty("udytils.htmlSnapshot.record", recordHtmlSnapshots)
    if (recordHtmlSnapshots) {
        outputs.upToDateWhen { false }
        outputs.cacheIf { false }
    }
}
