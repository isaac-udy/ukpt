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

    implementation(projects.platform.server.host)
    implementation(projects.platform.server.postgres)
    // :app:server is the only module allowed to depend on :platform:server:development, which
    // adds Zonky's embedded Postgres binaries to the runtime classpath.
    implementation(projects.platform.server.development)

    implementation(libs.logback)
    implementation(libs.koin.core)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverWebsockets)
    implementation(libs.ktor.server.auth)

    testImplementation(projects.platform.common.testing)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.koin.test)
}
