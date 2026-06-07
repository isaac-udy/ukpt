package architecture

import com.lemonappdev.konsist.api.Konsist

val projectScope = Konsist
    .scopeFromProject()
    .slice {
        !it.path.contains("embedded-enro") &&
                !it.path.contains("embedded-udytils") &&
                !it.path.contains("/src/test/") &&
                // Under AGP 9.0's `com.android.kotlin.multiplatform.library` plugin, Paparazzi host
                // tests live in the `androidHostTest` (formerly `androidUnitTest`) source set rather
                // than `src/test`. Exclude both so test fixtures aren't architecture-scanned.
                !it.path.contains("/src/androidHostTest/") &&
                !it.path.contains("/src/androidUnitTest/") &&
                !it.path.contains("/src/screenshotTest/")
    }