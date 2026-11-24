package ukpt

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test

/**
 * Runs `testAndroidHostTest` on JUnit 5: Jupiter runs the kotlin.test classes and Vintage runs
 * Paparazzi's JUnit 4 classes. Call this after evaluation, when the host test task exists.
 */
fun Project.runAndroidHostTestsOnJUnitPlatform() {
    if (!tasks.names.contains("testAndroidHostTest")) return
    val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
    listOf("junit-platformLauncher", "junit-jupiterEngine", "junit-vintageEngine").forEach { alias ->
        dependencies.add("androidHostTestRuntimeOnly", libs.findLibrary(alias).get())
    }
    dependencies.add("androidHostTestRuntimeOnly", dependencies.platform(libs.findLibrary("junit-bom").get()))
    tasks.named("testAndroidHostTest", Test::class.java) { useJUnitPlatform() }
}
