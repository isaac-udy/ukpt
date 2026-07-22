plugins {
    // Compose library + Paparazzi host-test wiring: the design-system docs are backed by
    // hand-written doc-surface snapshots (see design-system/README.md).
    id("ukpt.snapshot-testing")
}

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "platform.ui"
    }
    sourceSets {
        commonMain.dependencies {
            // `api` for anything whose types appear in the token surface: UkptColors exposes
            // Color, UkptTypography exposes TextStyle, and callers construct both.
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)

            // material3 is an implementation detail *while primitives are built from foundation*:
            // UkptTheme wraps a MaterialTheme so raw material internals (text-field decoration,
            // dividers, LocalContentColor) inherit the tokens, but no Ukpt* type exposes a
            // material3 type. A project that bases its primitives on Material3 instead — see
            // design-system/README.md, "What to build primitives on" — should promote this to
            // `api`, since material types then appear in the primitives' own surface.
            implementation(compose.material3)

            // Bundled design-system assets (fonts, drawables) live in commonMain/composeResources.
            implementation(compose.components.resources)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.udytils.snapshot)
        }
    }
}
