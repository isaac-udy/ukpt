plugins {
    id("ukpt.jvm-library")
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kotlinxRpc)
}

group = providers.gradleProperty("ukpt.projectNamespace").get()
version = "1.0.0"

application {
    mainClass.set("${providers.gradleProperty("ukpt.projectNamespace").get()}.ServerKt")

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

    implementation(libs.kotlinx.rpc.krpcServer)
    implementation(libs.kotlinx.rpc.krpcKtorServer)
    implementation(libs.kotlinx.rpc.krpcSerializationJson)

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}
