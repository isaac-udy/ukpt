plugins {
    id("ukpt.jvm-library")
}

dependencies {
    // Pages, fragments and routes in every feature's `server.web` are written against these.
    api(libs.udytils.htmx)
    api(libs.ktor.serverCore)
    api(libs.ktor.serverHtmlBuilder)
    api(libs.ktor.serverSse)
    implementation(libs.ktor.serverStatusPages)

    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.jsoup)
}
