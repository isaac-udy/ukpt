import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import ukpt.server.DevDatabaseEnvironment
import ukpt.server.DevDatabaseSubgraph
import ukpt.server.SmokeTestFatJarTask
import ukpt.server.VerifyRuntimeServiceFilesTask

/**
 * Convention plugin for the deployable server jar (`:app:server`).
 *
 * Applies: nothing
 * Configures: the Ktor/Shadow `shadowJar` task, and adds `verifyRuntimeServiceFiles` and
 * `smokeTestFatJar`.
 *
 * `mergeServiceFiles()` below is not the whole answer: `flyway-core` and
 * `flyway-database-postgresql` ship the same manifest path with different contents, and Shadow
 * 9.1.0 registers the merging transformer without it merging (see
 * `app/server/src/main/resources/META-INF/services/README.md`). `verifyRuntimeServiceFiles`
 * therefore checks the classpath rather than trusting a transformer, and `smokeTestFatJar` runs the
 * built jar, because a truncated manifest exists nowhere else.
 */

val serviceFileCheck = "verifyRuntimeServiceFiles"

plugins.withId("application") {
    val mainResourceDirectories = the<SourceSetContainer>()["main"].resources.srcDirs
    val overrideLocation = mainResourceDirectories.first().relativeToOrSelf(rootDir).invariantSeparatorsPath

    tasks.register<VerifyRuntimeServiceFilesTask>(serviceFileCheck) {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Fails when two runtime dependencies declare the same META-INF/services path, " +
            "which a fat jar would silently resolve by dropping one of them."
        runtimeClasspath.from(configurations.named("runtimeClasspath"))
        moduleResources.from(mainResourceDirectories)
        moduleResourceLocation.set(overrideLocation)
        report.set(layout.buildDirectory.file("reports/packaging/runtime-service-files.txt"))
    }

    tasks.named("check") {
        dependsOn(serviceFileCheck)
    }
}

// Ktor applies Shadow in response to the application plugin, so `shadowJar` doesn't exist yet in
// the `application` callback above.
pluginManager.withPlugin("com.gradleup.shadow") {
    tasks.withType<ShadowJar>().configureEach {
        // Gates building the jar rather than reporting afterwards: a jar with a collision in it is
        // already broken.
        dependsOn(serviceFileCheck)
        // Unreliable (see the plugin comment above), but where it works it is the real fix.
        mergeServiceFiles()
        dependencies {
            DevDatabaseSubgraph.moduleNotations.forEach { exclude(dependency(it)) }
            DevDatabaseSubgraph.projectPaths.forEach { exclude(project(it)) }
        }
    }

    // Read out here rather than inside the task-configuration action, where `the<…>()` would look
    // the extension up on the task instead of on the project.
    val applicationMainClass = the<JavaApplication>().mainClass
    val fatJarFile = tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile }
    val developmentClasspath = configurations.named("runtimeClasspath").map { configuration ->
        configuration.incoming
            .artifactView { componentFilter(DevDatabaseSubgraph.componentFilter()) }
            .files
    }

    tasks.register<SmokeTestFatJarTask>("smokeTestFatJar") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Builds the fat jar and boots it against a throwaway database, the way a " +
            "container would."
        dependsOn("buildFatJar")
        fatJar.set(fatJarFile)
        this.developmentClasspath.from(developmentClasspath)
        mainClass.set(applicationMainClass)
        forbiddenJarEntryPatterns.set(DevDatabaseSubgraph.forbiddenJarEntryPatterns)
        environment.put(DevDatabaseEnvironment.MODE, DevDatabaseEnvironment.MODE_EPHEMERAL)
        portVariable.set(DevDatabaseEnvironment.PORT)
        expectedLogFragments.set(
            listOf(
                // The two clobbered-manifest failure modes: Flyway throws, or finds nothing to do
                // and reports success.
                "Flyway migration complete:",
                // The dev database the jar itself does not contain came up off the added classpath.
                "Dev database: embedded-ephemeral",
            ),
        )
        bootTimeoutSeconds.set(90L)
        javaHome.set(providers.systemProperty("java.home"))
        report.set(layout.buildDirectory.file("reports/packaging/fat-jar-smoke-test.txt"))
    }
}
