// The server half of the Subcutaneous Test harness: `featureServer(…)` runs a feature's server
// graph in-process on Ktor's test engine against an embedded Postgres cloned from a migrated
// template for every test, `featureClient(…)` adds the transport that connects to that server, and
// `inProcessServer` does the same for `:app:client:desktop`'s End To End Tests.
plugins {
    id("ukpt.jvm-base")
}

dependencies {
    api(projects.platform.common.subcutaneous)
    api(libs.udytils.postgres.core)

    implementation(projects.platform.server.host)
    implementation(projects.platform.server.postgres)
    implementation(projects.platform.client.network)
    implementation(libs.udytils.postgres.embedded)
    implementation(libs.ktor.serverTestHost)
    implementation(libs.koin.ktor)
}
