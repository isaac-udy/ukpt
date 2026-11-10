plugins {
    id("ukpt.jvm-library")
}

dependencies {
    api(projects.platform.server.web)
    api(libs.ktor.serverCore)
    api(libs.koin.core)

    implementation(projects.platform.server.postgres)
    implementation(libs.koin.ktor)

    testImplementation(projects.platform.common.testing)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.ktor.server.auth)
}
