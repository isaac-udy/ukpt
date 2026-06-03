plugins {
    id("ukpt.jvm-library")
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    api(projects.feature.core.api)

    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverWebsockets)
    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    implementation(libs.ktor.clientContentNegotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.enro.common)
    implementation(libs.udytils.core)
    implementation(libs.koin.core)

    implementation(libs.ktor.server.auth)

    testImplementation(libs.kotlin.testJunit)
}
