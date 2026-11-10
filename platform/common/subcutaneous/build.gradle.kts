// The Subcutaneous Test harness: a feature's production server Koin graph in-process on Ktor's test
// engine, an embedded Postgres cloned from a migrated template for every test, and a tab that runs
// htmx over the server's HTML for the page robots. `:feature:[name]:tests` modules depend on it, and
// `:app:server`'s End To End Tests use `inProcessServer`, which serves on a real port.
plugins {
    id("ukpt.jvm-base")
}

dependencies {
    api(projects.platform.common.testing)
    api(projects.platform.common.robots)
    api(libs.koin.core)
    api(libs.udytils.postgres.core)
    api(libs.kotlinx.coroutinesCore)

    implementation(projects.platform.server.host)
    implementation(projects.platform.server.postgres)
    implementation(projects.platform.server.web)
    implementation(libs.udytils.postgres.embedded)
    implementation(libs.ktor.serverTestHost)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.clientCore)
    implementation(libs.koin.ktor)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization)

    testImplementation(libs.kotlin.test)
}
