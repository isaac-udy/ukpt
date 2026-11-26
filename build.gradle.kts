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

        // The atlas plugin, same story.
        classpath(libs.udytils.atlasGradlePlugin)

        // The mutation testing plugin, applied by ukpt.mutation.
        classpath(libs.udytils.mutationGradlePlugin)

        // The tracing plugins, applied by :feature modules and :app:client:telemetry.
        classpath(libs.udytils.traceGradlePlugin)
    }
}

plugins {
    id("ukpt.template-maintenance")

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
    id("ukpt.test-coverage")
    id("ukpt.mutation")
}

// Expectations for the lines in each module that no architecture Construct claims, for
// `./gradlew testCoverageReport`. Category ids are those in architecture.definitions.TestCategories.
testCoverage {
    noTests(":app:client:android", "application entry points")
    testedBy(":app:client:common", "wiring", "subcutaneous")
    noTests(":app:client:desktop", "application entry point")
    noTests(":app:client:web", "application entry point")
    noTests(":app:client:web-html", "application entry point")
    testedBy(":app:server", "wiring")
    testedBy(":platform:client:design", "snapshot", "component", "check")
    testedBy(":platform:client:design-html", "check")
    testedBy(":platform:client:network", "adapter", "wiring")
    testedBy(":platform:client:ui", "component", "snapshot")
    noTests(":platform:common:robots", "test infrastructure")
    noTests(":platform:common:subcutaneous", "test infrastructure")
    noTests(":platform:common:subcutaneous-server", "test infrastructure")
    noTests(":platform:common:testing", "test infrastructure")
    noTests(":platform:server:development", "development tooling")
    testedBy(":platform:server:host", "subcutaneous", "adapter")
    testedBy(":platform:server:postgres", "adapter", "subcutaneous")
}

// Catalog rules for `./gradlew runMutants`. A mutant runs when a rule selects it; it is killed only
// by a test in a category that its Construct expects. Rule selectors: constructs(), packages(),
// files(); ignore("<id>", "<reason>") excludes an equivalent mutant by the id in the report.
mutation {
    // Removing one of these calls changes no behaviour a test can check: console output, DI wiring
    // that every test graph already exercises, log lines, and HTTP client timeouts.
    excludeCalls(
        "kotlin.io.println",
        "kotlin.io.print",
        "kotlin.Throwable.printStackTrace",
        "org.koin.*",
        "dev.isaacudy.udytils.urpc.koin.*",
        "org.slf4j.Logger.*",
        "io.ktor.client.plugins.HttpTimeoutConfig.*",
        "io.ktor.client.plugins.timeout",
    )
    rule("domain") { constructs("ClientDomain.*", "ServerDomain.*", "FeatureRules.Shared*") }
    rule("data") { constructs("ClientData.*", "ServerData.*") }
    rule("view-models") { constructs("ClientUi.ViewModel", "ClientUi.ViewModelState") }
    rule("server-services") { constructs("ServerServices.*") }
}

// Applied here, not in the plugins block: the root project's plugins block cannot
// resolve version-free plugin ids from the buildscript classpath — that only works
// in subprojects. Subproject plugins (architecture, postgres) use alias() because
// their plugins block inherits the root classpath; the root must apply() directly.
apply(plugin = "dev.isaacudy.udytils.atlas")
apply(plugin = "dev.isaacudy.udytils.trace-viewer")
