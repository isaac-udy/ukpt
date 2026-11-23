plugins {
    id("ukpt.feature-tests")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.platform.common.subcutaneousServer)
            implementation(projects.feature.core.client)
            implementation(projects.feature.core.server)
        }
        jvmMain.dependencies {
            api(projects.platform.common.robots)
        }
    }
}
