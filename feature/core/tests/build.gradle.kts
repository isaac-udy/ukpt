plugins {
    id("ukpt.feature-tests")
}

dependencies {
    api(projects.platform.common.subcutaneous)
    api(projects.platform.common.robots)
    implementation(projects.feature.core.server)
}
