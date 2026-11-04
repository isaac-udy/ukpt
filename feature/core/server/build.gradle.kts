plugins {
    id("ukpt.jvm-library")
}

kotlin {
    compilerOptions {
        // The generated Exposed tables type UUID id columns as kotlin.uuid.Uuid.
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
}

dependencies {
    api(projects.feature.core.api)

    // The generated tables, Exposed, and the PgNotificationBus (docs/serverdata.md).
    implementation(projects.platform.server.postgres)

    // Makes the `@ArchitectureException` annotation importable so server declarations can
    // declare rule-scoped exemptions (a tiny artifact — no Konsist or test machinery).
    implementation(libs.udytils.architectureAnnotations)

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

    testImplementation(projects.platform.common.testing)
    testImplementation(libs.kotlin.test)
}
