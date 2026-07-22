/**
 * Convention plugin for Compose modules that snapshot-test their UI with Paparazzi.
 *
 * Applies: ukpt.compose-library, Paparazzi
 *
 * This builds on the compose-library convention and adds the host-test component Paparazzi
 * attaches to, plus the stub-R classpath workaround it needs to run. A module applying this still
 * declares its own `androidHostTest` dependency on `dev.isaacudy.udytils:snapshot` (the harness)
 * and its own `PreviewSnapshotTest` naming the package tree to scan — those are the module's
 * facts, not boilerplate.
 *
 * Snapshot tasks must be run with `--no-configuration-cache`: Paparazzi's resource-preparation
 * task cannot be stored in the configuration cache.
 */
plugins {
    id("ukpt.compose-library")
    id("app.cash.paparazzi")
}

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        // Host (JVM) unit-test component — Paparazzi attaches its record/verify tasks here and
        // reads the KMP `androidHostTest` source set.
        withHostTestBuilder {
        }.configure {
        }
    }
}

// Paparazzi loads R classes (this module's + every dependency's: dev.enro.R, androidx.*.R, …)
// reflectively at runtime. The KMP library plugin puts the aggregated host-test stub R jar on the
// *compile* classpath only, so add it to the test runtime classpath via doFirst (which wins over
// AGP's lazily-provided AndroidUnitTest classpath); otherwise the tests fail with
// ClassNotFoundException. Adding it to androidHostTestRuntimeOnly would create a cycle, since the
// stub-R task consumes the runtime classpath as input.
tasks.withType<Test>().configureEach {
    if (name == "testAndroidHostTest") {
        dependsOn("generateAndroidHostTestStubRFile")
        // Bound outside the doFirst so the closure captures only this FileCollection: capturing
        // `layout`/`project` would drag the whole script into the task's configuration-cache state.
        val rJar = files(
            layout.buildDirectory.file("intermediates/compile_and_runtime_r_class_jar/androidHostTest/generateAndroidHostTestStubRFile/R.jar")
        )
        doFirst {
            classpath = classpath + rJar
        }
    }
}
