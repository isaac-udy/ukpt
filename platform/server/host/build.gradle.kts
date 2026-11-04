plugins {
    id("ukpt.jvm-library")
}

dependencies {
    api(libs.ktor.serverCore)
    api(libs.koin.core)

    implementation(projects.platform.server.postgres)
    implementation(libs.koin.ktor)
    implementation(libs.ktor.serverWebsockets)
    implementation(libs.urpc.koin)
}
