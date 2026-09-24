plugins {
    id("ukpt.jvm-library")
    id("ukpt.html-snapshot")
}

dependencies {
    api(projects.feature.core.api)

    // Makes the `@ArchitectureException` annotation importable so server declarations can
    // declare rule-scoped exemptions (a tiny artifact — no Konsist or test machinery).
    implementation(libs.udytils.architectureAnnotations)

    implementation(projects.platform.server.web)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    implementation(libs.ktor.clientContentNegotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.udytils.core)
    implementation(libs.koin.core)

    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.jsoup)
    testImplementation(libs.udytils.htmlSnapshot)
}
