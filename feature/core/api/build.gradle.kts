plugins {
    id("ukpt.kmp-library")
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kotlinxRpc)
}

android {
    namespace = "feature.core.api"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.rpc.krpcSerializationJson)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.websockets)

            compileOnly(libs.kotlinx.rpc.krpcClient)
            compileOnly(libs.kotlinx.rpc.krpcServer)
            api(libs.kotlinx.coroutinesCore)
            api(libs.kotlinx.serialization)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.rpc.core)
            api(libs.kotlinx.rpc.krpcCore)
            api(libs.enro.common)
            api(libs.udytils.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
