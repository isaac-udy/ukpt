plugins {
    id("ukpt.kmp-library")
    id("ukpt.html-web")
}

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "platform.network"
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.ktor.clientCore)
            api(libs.urpc.client)
            api(libs.koin.core)
            implementation(libs.ktor.clientWebsockets)
        }
        // CIO pulls ktor-network, which imports `node:net` and breaks the web bundles.
        jvmMain.dependencies {
            implementation(libs.ktor.clientCio)
        }
        androidMain.dependencies {
            implementation(libs.ktor.clientCio)
        }
        iosMain.dependencies {
            implementation(libs.ktor.clientCio)
        }
        webMain.dependencies {
            implementation(libs.ktor.clientJs)
        }
    }
}
