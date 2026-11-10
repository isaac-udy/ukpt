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
    alias(libs.plugins.kover)
}

// Advisory coverage of the feature modules by the Subcutaneous Tests alone: only each
// `:feature:[name]:tests` module's `subcutaneousTest` task is instrumented. No verification bounds.
kover {
    merge {
        subprojects { it.path.startsWith(":feature:") && it.name in setOf("client", "server", "tests") }
        instrumentation {
            disabledForAll.set(!project.path.endsWith(":tests"))
            disabledForTestTasks.addAll("test", "unitTest", "componentTest", "wiringTest", "adapterTest", "checkTest", "endToEndTest")
        }
    }
    reports {
        filters {
            excludes { packages("feature.*.tests", "enro_generated_bindings", "*.generated.resources") }
        }
    }
}

tasks.register("subcutaneousCoverage") {
    group = "verification"
    description = "Writes the feature modules' coverage by Subcutaneous Tests to build/reports/kover (HTML and XML)."
    dependsOn("koverHtmlReport", "koverXmlReport")
}
