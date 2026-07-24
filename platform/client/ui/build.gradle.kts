plugins {
    // Compose library + Paparazzi host-test wiring: common components are snapshot-tested the same
    // preview-driven way as the design system (see ukpt.snapshot-testing). Ready for the first
    // component's `@Preview`; nothing is snapshotted until one exists.
    id("ukpt.snapshot-testing")
}

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "platform.ui"
    }
    sourceSets {
        commonMain.dependencies {
            // Common platform UI builds on the design system: it composes the design-system
            // primitives and reads its tokens into larger, reusable components. `api` re-exposes the
            // design system — and the Compose runtime/foundation/ui it already exposes — so a
            // consumer of this module sees both without re-declaring them.
            api(projects.platform.client.design)

            // Unlike :platform:client:design, this module MAY depend on navigation and DI — that
            // separation is the whole reason it exists (see DesignSystemRules.noNavigationOrDi).
            // Uncomment as the first real component needs them, matching :feature:core:client:
            //   implementation(compose.material3)                  // Material3 building blocks
            //   implementation(compose.preview)                    // @Preview, found by PreviewSnapshotTest
            //   implementation(compose.components.uiToolingPreview)
            //   implementation(compose.components.resources)       // bundled assets (fonts, drawables)
            //   implementation(libs.enro.core)                     // navigation — add libs.enro.processor via KSP
            //   implementation(libs.koin.core)                     // dependency injection
            //   implementation(libs.udytils.ui)                    // shared udytils UI helpers
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        getByName("androidHostTest").dependencies {
            // The snapshot harness, ready for the first component's preview goldens.
            implementation(libs.udytils.snapshot)
        }
    }
}
