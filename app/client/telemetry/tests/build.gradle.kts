plugins {
    id("ukpt.feature-tests")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.app.client.telemetry)
            implementation(libs.udytils.traceEnro)
            implementation(projects.feature.core.tests)
            implementation(projects.feature.core.client)
            implementation(libs.androidx.lifecycle.viewmodel)
        }
    }
}
