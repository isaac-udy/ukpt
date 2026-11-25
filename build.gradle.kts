buildscript {
    dependencies {
        // The udytils architecture Gradle plugin, substituted from the embedded-udytils composite
        // build. It lives on the buildscript classpath (not pluginManagement) because including
        // embedded-udytils in pluginManagement silently disables the explicit dependency
        // substitutions in settings.gradle.kts. With the classes on the root build classpath,
        // subprojects can apply it with a plain `plugins { id(...) }` block, no version needed.
        classpath(libs.udytils.architectureGradlePlugin)

        // The Postgres codegen plugin, on the classpath for the same reason: resolving it through
        // pluginManagement instead would lose the substitutions above.
        classpath(libs.udytils.postgres.gradlePlugin)
    }
}

plugins {
    id("ukpt.template-maintenance")

    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    id("ukpt.test-coverage")
}

// Expectations for the lines in each module that no architecture Construct claims, for
// `./gradlew testCoverageReport`. Category ids are those in architecture.definitions.TestCategories.
testCoverage {
    testedBy(":app:server", "wiring")
    noTests(":platform:common:browser", "test infrastructure")
    noTests(":platform:common:robots", "test infrastructure")
    noTests(":platform:common:subcutaneous", "test infrastructure")
    noTests(":platform:common:testing", "test infrastructure")
    noTests(":platform:server:development", "development tooling")
    testedBy(":platform:server:host", "subcutaneous", "adapter")
    testedBy(":platform:server:postgres", "adapter", "subcutaneous")
    testedBy(":platform:server:web", "adapter", "subcutaneous")
}
