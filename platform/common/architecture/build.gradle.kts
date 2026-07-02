plugins {
    alias(libs.plugins.kotlinJvm)
    // Adds the architectureTest source set and the standalone verifyArchitecture /
    // updateArchitectureDocumentation tasks (plain `test` runs nothing here). The test classes
    // themselves are generated into build/generated/ from the definition below — none are checked
    // in. Resolves version-free from the root buildscript classpath (see the root build file).
    alias(libs.plugins.udytilsArchitecture)
}

configure<dev.isaacudy.udytils.architecture.gradle.ArchitectureExtension> {
    definition.set("architecture.rules.UkptArchitecture")
}

dependencies {
    // The rule catalog lives in this module's main source set and programs against the DSL.
    api(libs.udytils.architectureCore)
}
