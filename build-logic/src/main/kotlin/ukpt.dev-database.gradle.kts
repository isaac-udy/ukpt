import ukpt.server.DevDatabaseEnvironment

/**
 * Convention plugin for a server application that boots against the embedded dev database.
 *
 * Applies: nothing
 * Configures: the `application` plugin's `run` task (dev-database environment defaults) and adds
 * a `wipeDevDatabase` task.
 *
 * `./gradlew :app:server:run` should just work, with a database that still holds what you put in
 * it last time — so `run` defaults `UKPT_DEV_DB=embedded` and points `UKPT_DEV_DB_DIR` at this
 * module's build directory. Both defaults yield to the invoking environment, so
 * `UKPT_DEV_DB=ephemeral ./gradlew …` gets a throwaway database and `UKPT_DEV_DB= ./gradlew …`
 * gets no dev database at all (the app then reads `POSTGRES_URL` and friends).
 *
 * What the dev database must never do is ship: `ukpt.server-packaging` is what keeps it out of the
 * deployable.
 */

// Reading the environment through `providers` rather than System.getenv() makes each variable a
// declared configuration-cache input: changing one invalidates the cached graph instead of being
// silently baked into it.
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
