rootProject.name = providers.gradleProperty("ukpt.projectName").get()

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

apply(from = "gradle/embedded-sdk-location.settings.gradle.kts")

include(":app:server")

include(":feature:core:api")
include(":feature:core:server")

include(":platform:common:architecture")
include(":platform:server:postgres")
include(":platform:server:development")

includeBuild("embedded-udytils") {
    name = "embedded-udytils"
    dependencySubstitution {
        substitute(module("dev.isaacudy.udytils:core")).using(project(":core"))
        substitute(module("dev.isaacudy.udytils:ui")).using(project(":ui"))
        substitute(module("dev.isaacudy.udytils:snapshot")).using(project(":snapshot"))
        substitute(module("dev.isaacudy.udytils:htmx")).using(project(":htmx"))
        substitute(module("dev.isaacudy.udytils:html-snapshot")).using(project(":html-snapshot"))
        substitute(module("dev.isaacudy.udytils:urpc-protocol")).using(project(":urpc:protocol"))
        substitute(module("dev.isaacudy.udytils:urpc-client")).using(project(":urpc:client"))
        substitute(module("dev.isaacudy.udytils:urpc-client-rest")).using(project(":urpc:client-rest"))
        substitute(module("dev.isaacudy.udytils:urpc-server")).using(project(":urpc:server"))
        substitute(module("dev.isaacudy.udytils:urpc-koin")).using(project(":urpc:koin"))
        substitute(module("dev.isaacudy.udytils:urpc-processor")).using(project(":urpc:processor"))
        substitute(module("dev.isaacudy.udytils:postgres-core")).using(project(":postgres-core"))
        substitute(module("dev.isaacudy.udytils:postgres-koin")).using(project(":postgres-koin"))
        substitute(module("dev.isaacudy.udytils:postgres-embedded")).using(project(":postgres-embedded"))
        substitute(module("dev.isaacudy.udytils:postgres-gradle-plugin")).using(project(":postgres-gradle-plugin"))
        // Never declared by a build file — the postgres Gradle plugin resolves it in its own
        // detached `postgresCodegenEngine` configuration — so without this it goes to Maven Central.
        substitute(module("dev.isaacudy.udytils:postgres-codegen")).using(project(":postgres-codegen"))
        substitute(module("dev.isaacudy.udytils:architecture-core")).using(project(":architecture-core"))
        substitute(module("dev.isaacudy.udytils:architecture-annotations")).using(project(":architecture-annotations"))
        substitute(module("dev.isaacudy.udytils:architecture-gradle-plugin")).using(project(":architecture-gradle-plugin"))
        substitute(module("dev.isaacudy.udytils:atlas-core")).using(project(":atlas-core"))
        substitute(module("dev.isaacudy.udytils:atlas-gradle-plugin")).using(project(":atlas-gradle-plugin"))
    }
}
