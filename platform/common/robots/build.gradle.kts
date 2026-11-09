// Screen robots: the base that each feature's robots build on. Component Tests and End To End Tests
// drive screens only through robots (see the Testing architecture rules).
plugins {
    id("ukpt.jvm-base")
}

dependencies {
    api(projects.platform.common.testing)
    api(libs.compose.uiTest)
    api(libs.enro.core)
}
