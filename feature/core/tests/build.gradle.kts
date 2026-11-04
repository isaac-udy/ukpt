plugins {
    id("ukpt.feature-tests")
}

dependencies {
    api(projects.platform.common.subcutaneous)
    implementation(projects.feature.core.client)
    implementation(projects.feature.core.server)
}
