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
