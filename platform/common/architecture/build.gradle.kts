// The architecture Gradle plugin arrives via buildscript-classpath substitution from the
// embedded-udytils composite build (including that build in pluginManagement would silently
// disable the explicit dependency substitutions in settings.gradle.kts).
buildscript {
    dependencies {
        classpath(libs.udytils.architectureGradlePlugin)
    }
}

plugins {
    alias(libs.plugins.kotlinJvm)
}

// Adds the architectureTest source set and the standalone verifyArchitecture /
// updateArchitectureDocumentation tasks (plain `test` runs nothing here).
apply(plugin = "dev.isaacudy.udytils.architecture")

dependencies {
    // The rule catalog lives in this module's main source set and programs against the DSL.
    api(libs.udytils.architectureCore)
}
