import ukpt.server.DevDatabaseEnvironment

/**
 * Convention plugin for a server application that boots against the embedded dev database.
 *
 * Applies: nothing
 * Configures: the `application` plugin's `run` task (dev-database environment) and adds a
 * `wipeDevDatabase` task.
 *
 * `run` defaults `UKPT_DEV_DB=embedded` and points `UKPT_DEV_DB_DIR` at this module's build
 * directory. Both defaults yield to the invoking environment, so `UKPT_DEV_DB=ephemeral ./gradlew …`
 * gets a throwaway database and `UKPT_DEV_DB= ./gradlew …` no dev database at all.
 * `UKPT_DEV_SCENARIO` is passed through only when the invoking environment sets it.
 */

val devDatabaseDirectory = layout.buildDirectory.dir("dev-postgres")

plugins.withId("application") {
    tasks.named<JavaExec>("run") {
        // JavaExec snapshots the whole process environment at configuration time, and the
        // configuration cache stores that snapshot with the task without fingerprinting it, so a
        // variable set on one run would be replayed by every cached run after it. A provider first
        // read inside doFirst is evaluated on every build, and an absent scenario is removed from
        // the snapshot rather than inherited from the run that stored the entry.
        val requestedMode = providers.environmentVariable(DevDatabaseEnvironment.MODE)
        val requestedDirectory = providers.environmentVariable(DevDatabaseEnvironment.DIRECTORY)
        val requestedScenario = providers.environmentVariable(DevDatabaseEnvironment.SCENARIO)
        val defaultDirectory = devDatabaseDirectory.get().asFile.absolutePath
        doFirst {
            environment(DevDatabaseEnvironment.MODE, requestedMode.getOrElse(DevDatabaseEnvironment.MODE_EMBEDDED))
            environment(DevDatabaseEnvironment.DIRECTORY, requestedDirectory.getOrElse(defaultDirectory))
            val scenario = requestedScenario.orNull
            if (scenario == null) environment.remove(DevDatabaseEnvironment.SCENARIO)
            else environment(DevDatabaseEnvironment.SCENARIO, scenario)
        }
    }
}

tasks.register<Delete>("wipeDevDatabase") {
    group = "database"
    description = "Deletes the embedded dev database, so the next run starts from a fresh, " +
        "re-seeded cluster."
    delete(devDatabaseDirectory)
}
