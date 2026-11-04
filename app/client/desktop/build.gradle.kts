import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("ukpt.jvm-base")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

private val projectNamespace = providers.gradleProperty("ukpt.projectNamespace").get()

dependencies {
    implementation(projects.app.client.common)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    testImplementation(projects.platform.common.testing)
    testImplementation(projects.platform.common.subcutaneous)
    testImplementation(projects.feature.core.tests)
    testImplementation(projects.feature.core.api)
    testImplementation(projects.app.server)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.koin.compose)
    testImplementation(libs.kotlin.test)
}

// Each test JVM starts an embedded Postgres for the End To End Tests.
tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    maxHeapSize = "1g"
}

compose.desktop {
    application {
        mainClass = "$projectNamespace.MainKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Deb,
            )
            packageName = projectNamespace
            packageVersion = "1.0.0"
        }
    }
}
