import ukpt.server.DevDatabaseEnvironment

/**
 * Convention plugin for a server application that boots against the embedded dev database.
 *
 * Applies: nothing
 * Configures: the `application` plugin's `run` task (dev-database environment defaults) and adds
 * a `wipeDevDatabase` task.
 *
 * `run` defaults `UKPT_DEV_DB=embedded` and points `UKPT_DEV_DB_DIR` at this module's build
 * directory. Both defaults yield to the invoking environment, so `UKPT_DEV_DB=ephemeral ./gradlew …`
 * gets a throwaway database and `UKPT_DEV_DB= ./gradlew …` no dev database at all.
 */

// Read through `providers`, not System.getenv(): that makes each variable a declared
// configuration-cache input rather than a value baked silently into the cached graph.
val requestedMode = providers.environmentVariable(DevDatabaseEnvironment.MODE)
val requestedDirectory = providers.environmentVariable(DevDatabaseEnvironment.DIRECTORY)
val devDatabaseDirectory = layout.buildDirectory.dir("dev-postgres")

plugins.withId("application") {
    tasks.named<JavaExec>("run") {
        environment(DevDatabaseEnvironment.MODE, requestedMode.getOrElse(DevDatabaseEnvironment.MODE_EMBEDDED))
        environment(
            DevDatabaseEnvironment.DIRECTORY,
            requestedDirectory.getOrElse(devDatabaseDirectory.get().asFile.absolutePath),
        )
    }
}

tasks.register<Delete>("wipeDevDatabase") {
    group = "database"
    description = "Deletes the embedded dev database, so the next run starts from a fresh, " +
        "re-seeded cluster."
    delete(devDatabaseDirectory)
}
