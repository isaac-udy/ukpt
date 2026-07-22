/**
 * Convention plugin for KMP modules that also use Compose Multiplatform.
 *
 * Applies: ukpt.kmp-library, ComposeMultiplatform, ComposeCompiler
 *
 * This builds on the kmp-library convention and adds Compose support.
 */
plugins {
    id("ukpt.kmp-library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        // The AGP KMP library plugin disables Android resource processing by default, and with it
        // the module's assets artifact. Compose Multiplatform packages this module's
        // `composeResources` (strings .cvr, fonts, drawables) as Android *assets*, so without this
        // flag the pack never reaches the consuming APK and every stringResource/Font lookup from
        // this module fails at runtime — silently for fonts, MissingResourceException for strings.
        // Snapshot tests don't catch it (host-JVM tests read resources from the classpath, not APK
        // assets), and stale merged assets in consumers mask it until a clean build.
        //
        // This also generates the R class host tests need: Paparazzi resolves R reflectively at
        // runtime, so a Compose module without it dies with ClassNotFoundException: <module>.R.
        androidResources {
            enable = true
        }
    }
}
