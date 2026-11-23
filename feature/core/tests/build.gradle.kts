plugins {
    id("ukpt.feature-tests")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.platform.common.subcutaneousServer)
            api(projects.platform.common.robots)
            implementation(projects.feature.core.client)
            implementation(projects.feature.core.server)
        }
    }
}
