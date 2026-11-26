plugins {
    id("ukpt.kmp-library")
    id("dev.isaacudy.udytils.trace-site-table")
}

private val projectNamespace = providers.gradleProperty("ukpt.projectNamespace").get()
private val architecture = ":platform:common:architecture"

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "$projectNamespace.telemetry"
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.udytils.traceRuntime)
            implementation(projects.feature.core.client)
        }
        commonTest.dependencies {
            implementation(projects.feature.core.tests)
            implementation(projects.platform.common.testing)
            implementation(libs.kotlin.test)
        }
    }
}

traceSiteTable {
    classification.set(project(architecture).layout.buildDirectory.file("reports/architecture/classification.json"))
    packageName.set("$projectNamespace.telemetry")
}

dependencies {
    traceSites(projects.feature.core.client)
}

tasks.named("generateTraceSiteTable") {
    dependsOn("$architecture:classifyArchitecture")
}
