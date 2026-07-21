package architecture

import com.lemonappdev.konsist.api.Konsist

/**
 * Matches any test source set so none of them are architecture-scanned: plain-JVM `src/test`, KMP's
 * shared `commonTest`, every per-platform set (`desktopTest`, `iosSimulatorArm64Test`, `wasmJsTest`,
 * …), and Android's `androidHostTest`/`androidUnitTest`/`screenshotTest` (under AGP 9.0's
 * `com.android.kotlin.multiplatform.library` plugin, Paparazzi host tests live in `androidHostTest`,
 * formerly `androidUnitTest`, rather than `src/test`). The `[^/]*[Tt]est` segment matches the source
 * set directly after `/src/`; no `…Main` set ends in `test`, so production code is never excluded.
 */
private val testSourceSet = Regex("/src/[^/]*[Tt]est/")

val projectScope = Konsist
    .scopeFromProject()
    .slice {
        // The architecture module itself (the rule catalog + definition) is meta-code,
        // not governed code — scanning it would classify the catalog's own objects.
        !it.path.contains("/platform/common/architecture/") &&
                !it.path.contains("embedded-enro") &&
                !it.path.contains("embedded-udytils") &&
                // build-logic is an includeBuild composite of template tooling (not app code),
                // sibling to the embedded composite builds — its sources aren't governed either.
                !it.path.contains("/build-logic/") &&
                // Test sources of every kind are out of scope (see testSourceSet above).
                !testSourceSet.containsMatchIn(it.path)
    }