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
 * Where `ukpt.dev-database` makes `run` boot against a local database, this plugin is about the
 * artifact that leaves the machine: the dev database's ~150 MB of Postgres binaries are cut out of
 * the fat jar, and both hazards of packing an application into one jar — a dependency silently
 * missing, a ServiceLoader manifest silently truncated — get a gate.
 *
 * The truncation hazard is why `mergeServiceFiles()` below is not the whole answer: `flyway-core`
 * and `flyway-database-postgresql` ship the same manifest path with different contents, and Shadow
 * 9.1.0 registers the merging transformer without it merging (confirmed in this repository — see
 * `app/server/src/main/resources/META-INF/services/README.md`). `verifyRuntimeServiceFiles`
 * therefore looks for the collision on the classpath rather than trusting a transformer, and
 * `smokeTestFatJar` runs the built jar, because a truncated manifest exists nowhere else.
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

// Shadow arrives after the application plugin — Ktor applies it in response — so everything that
// names `shadowJar` waits for it rather than for `application`.
pluginManager.withPlugin("com.gradleup.shadow") {
    tasks.withType<ShadowJar>().configureEach {
        // A jar built without checking for manifest collisions may already be broken, so the check
        // gates building one rather than reporting on it afterwards.
        dependsOn(serviceFileCheck)
        // Correct in principle, unreliable in practice (see the plugin comment above): kept because
        // where it works it is the real fix, and it can only ever help.
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
        forbiddenJarEntryMarkers.set(DevDatabaseSubgraph.forbiddenJarEntryMarkers)
        environment.put(DevDatabaseEnvironment.MODE, DevDatabaseEnvironment.MODE_EPHEMERAL)
        portVariable.set(DevDatabaseEnvironment.PORT)
        expectedLogFragments.set(
            listOf(
                // Flyway ran through PostgresMigrator rather than throwing or reporting nothing,
                // which is what the two clobbered-manifest failure modes look like.
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
