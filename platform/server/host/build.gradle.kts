plugins {
    id("ukpt.jvm-library")
}

dependencies {
    api(projects.platform.server.web)
    api(libs.ktor.serverCore)
    api(libs.koin.core)

    implementation(projects.platform.server.postgres)
    implementation(libs.koin.ktor)
}
