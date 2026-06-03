plugins {
    id("ukpt.kmp-library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "feature.core.api"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.websockets)

            api(libs.kotlinx.coroutinesCore)
            api(libs.kotlinx.serialization)
            api(libs.kotlinx.datetime)
            api(libs.enro.common)
            api(libs.udytils.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
