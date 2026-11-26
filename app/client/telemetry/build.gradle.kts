plugins {
    id("ukpt.kmp-library")
    id("dev.isaacudy.udytils.trace-site-table")
}

// The trace plugin rewrites the on(…) references of the rules; it instruments no telemetry code.
apply(plugin = "dev.isaacudy.udytils.trace")
configure<dev.isaacudy.udytils.trace.gradle.TraceExtension> {
    packages.add("feature")
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
            implementation(libs.kotlinx.serialization)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(projects.feature.core.client)
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
