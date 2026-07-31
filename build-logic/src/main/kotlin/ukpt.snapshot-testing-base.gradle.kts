/**
 * Leaf convention for Paparazzi snapshot-test wiring, independent of the module's target set.
 *
 * Applies: KotlinMultiplatform, the AGP 9 Android KMP library plugin, Paparazzi
 *
 * Most modules should apply `ukpt.snapshot-testing`, which composes this with the template's
 * standard target set via `ukpt.compose-library`. This leaf exists for shell modules that
 * hand-wire their own targets (a named `jvm("desktop")` target, an iOS framework binary, …) and
 * therefore can't apply `ukpt.compose-library` without gaining a second, unwanted `jvm()` target.
 * The plugins applied here are the ones such a module already has — applying them again is a
 * no-op — and none of them declares a non-Android target.
 *
 * An Android target is required: Paparazzi runs as an Android host test. A module applying this
 * still declares its own `androidHostTest` dependency on `dev.isaacudy.udytils:snapshot` (the
 * harness) and its own `PreviewSnapshotTest` naming the package tree to scan — those are the
 * module's facts, not boilerplate.
 *
 * Snapshot tasks must be run with `--no-configuration-cache`: Paparazzi's resource-preparation
 * task cannot be stored in the configuration cache.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
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

        // The AGP KMP library plugin disables Android resource processing by default, and with it
        // the R class Paparazzi resolves reflectively at runtime — without this flag the host
        // tests die with ClassNotFoundException: <module>.R. ukpt.compose-library also enables it
        // (for its own reason: packaging composeResources as Android assets); setting it twice is
        // harmless, and a hand-wired module applying only this leaf needs it from here.
        androidResources {
            enable = true
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
