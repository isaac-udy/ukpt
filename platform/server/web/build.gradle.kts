plugins {
    id("ukpt.jvm-library")
}

dependencies {
    // Pages, fragments and routes in every feature's `server.web` are written against these.
    api(libs.udytils.htmx)
    api(libs.ktor.serverCore)
    api(libs.ktor.serverHtmlBuilder)
    api(libs.ktor.serverSse)
    api(libs.ktor.serverWebsockets)
    implementation(libs.ktor.serverStatusPages)
    implementation(libs.kotlinx.serialization)

    testImplementation(projects.platform.common.testing)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.jsoup)
}
