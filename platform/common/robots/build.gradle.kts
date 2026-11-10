// Page robots: the base that each feature's robots build on. Subcutaneous Tests and End To End Tests
// drive pages only through robots (see the Testing architecture rules).
plugins {
    id("ukpt.jvm-base")
}

dependencies {
    api(projects.platform.common.testing)
}
