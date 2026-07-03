buildscript {
    dependencies {
        // The udytils architecture Gradle plugin, substituted from the embedded-udytils composite
        // build. It lives on the buildscript classpath (not pluginManagement) because including
        // embedded-udytils in pluginManagement silently disables the explicit dependency
        // substitutions in settings.gradle.kts. With the classes on the root build classpath,
        // subprojects can apply it with a plain `plugins { id(...) }` block, no version needed.
        classpath(libs.udytils.architectureGradlePlugin)
        // The udytils metrics Gradle plugin (same classpath mechanism as above).
        classpath(libs.udytils.metricsGradlePlugin)
    }
}

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.kotlinKsp) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

// Applied by class rather than by id: the root project's own plugins { } block cannot see the
// buildscript classpath above (subprojects can, which is how the architecture plugin is applied).
apply<dev.isaacudy.udytils.metrics.gradle.MetricsPlugin>()

// Codebase health metrics: `collectMetrics` gathers every integration,
// `publishMetrics` appends the run to the `metrics` branch, and
// `generateMetricsReport` renders build/metrics/report/ from the series.
configure<dev.isaacudy.udytils.metrics.gradle.MetricsExtension> {
    integrations {
        architecture(":platform:common:architecture")
        linesOfCode()
        readmeHealth()
        buildWarnings()
    }
}
