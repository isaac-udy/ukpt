plugins {
    id("ukpt.jvm-library")
}

dependencies {
    api(libs.ktor.serverCore)
    api(libs.koin.core)
    api(libs.ktor.serverWebsockets)
    api(libs.urpc.server)

    implementation(projects.platform.server.postgres)
    implementation(libs.koin.ktor)
    implementation(libs.urpc.koin)
}
