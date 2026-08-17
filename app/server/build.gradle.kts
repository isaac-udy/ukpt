plugins {
    id("ukpt.jvm-server")
    id("ukpt.dev-database")
    id("ukpt.server-packaging")
    alias(libs.plugins.ktor)
}

private val projectNamespace = providers.gradleProperty("ukpt.projectNamespace").get()

group = projectNamespace
version = "1.0.0"

application {
    mainClass.set("$projectNamespace.ServerKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(projects.feature.core.server)

    implementation(projects.platform.server.postgres)
    // The only module allowed to depend on this: it carries Zonky's embedded Postgres binaries.
    implementation(projects.platform.server.development)

    implementation(libs.logback)
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverWebsockets)
    implementation(libs.ktor.server.auth)

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}
