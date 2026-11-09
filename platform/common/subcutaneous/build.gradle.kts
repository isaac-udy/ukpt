// The Subcutaneous Test harness: a feature's production client and server Koin graphs, the
// server in-process on Ktor's test engine, and an embedded Postgres cloned from a migrated template
// for every test. Only `:feature:[name]:tests` modules depend on it.
plugins {
    id("ukpt.jvm-base")
}

dependencies {
    api(projects.platform.common.testing)
    api(libs.koin.core)
    api(libs.enro.test)
    api(libs.udytils.viewmodel)
    api(libs.udytils.postgres.core)
    api(libs.kotlinx.coroutinesCore)

    implementation(projects.platform.server.host)
    implementation(projects.platform.server.postgres)
    implementation(projects.platform.client.network)
    implementation(libs.udytils.postgres.embedded)
    implementation(libs.ktor.serverTestHost)
    implementation(libs.koin.ktor)
    implementation(libs.kotlinx.coroutinesTest)
    implementation(libs.androidx.lifecycle.viewmodel)
}
