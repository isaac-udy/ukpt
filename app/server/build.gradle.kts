plugins {
    id("ukpt.jvm-server")
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
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

    implementation(libs.logback)
    implementation(libs.koin.core)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverWebsockets)
    implementation(libs.ktor.server.auth)

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}
