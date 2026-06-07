plugins {
    id("ukpt.jvm-library")
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    api(projects.feature.core.api)

    // Makes the `@ArchitectureException` annotation (in :platform:common:architecture's main
    // source set) importable so server declarations can declare rule-scoped exemptions.
    implementation(projects.platform.common.architecture)

    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverWebsockets)
    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    implementation(libs.ktor.clientContentNegotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.enro.common)
    implementation(libs.udytils.core)
    implementation(libs.koin.core)
    // urpc-koin provides the server binding runtime + the UrpcCall scope qualifier
    // (transitively urpc-server + urpc-protocol) for hosting @Urpc services.
    implementation(libs.urpc.koin)

    implementation(libs.ktor.server.auth)

    testImplementation(libs.kotlin.testJunit)
}
