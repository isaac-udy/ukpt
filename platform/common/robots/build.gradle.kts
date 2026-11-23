// Screen robots: the base that each feature's robots build on. Component Tests and End To End Tests
// drive screens only through robots (see the Testing architecture rules).
plugins {
    id("ukpt.kmp-library")
    id("ukpt.html-web")
}

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "platform.testing.robots"
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.platform.common.testing)
            api(libs.compose.uiTest)
            api(libs.enro.core)
        }
    }
}
