# `run` reads the dev-database switches at execution time

A `<PREFIX>_DEV_SCENARIO` set on one `./gradlew :app:server:run` was replayed by every later `run`
that reused the configuration-cache entry, from any shell, whether or not the variable was set.
Against a persistent cluster the second run then failed at boot:

```
java.lang.IllegalStateException: Dev scenario '<name>' was explicitly requested, but the dev
database at …/app/server/build/dev-postgres/pg<major> already contains data.
```

`JavaExec.environment` defaults to a snapshot of the Gradle process environment taken at
configuration time. The configuration cache stores that snapshot with the task and does not
fingerprint it, so a later build whose environment differs reuses the entry and forks the server
with the stored snapshot. `<PREFIX>_DEV_DB` and `<PREFIX>_DEV_DB_DIR` were not affected only
because the build script read them at configuration time through `providers.environmentVariable`,
which registers each as a configuration-cache input; nothing in build logic read the scenario
variable, so nothing tracked it.

`ukpt.dev-database` now sets all three switches inside the `run` task's `doFirst`. A
`providers.environmentVariable` provider first read at execution time is evaluated on every build,
and an absent scenario is removed from the snapshot rather than inherited from the run that stored
the entry. A change to any of the three switches reuses the cached entry instead of invalidating it.
`ukpt.server.DevDatabaseEnvironment` gains `SCENARIO`.

## Detection

Any project whose `run` task passes `<PREFIX>_DEV_SCENARIO` to the server through the inherited
environment is affected. The file sync carries the build-logic change for a project that still
uses the template's `ukpt.dev-database` plugin. A project that configures `run` in
`app/server/build.gradle.kts` or in its own convention plugin applies the change by hand.

```bash
grep -rn "DEV_SCENARIO" build-logic/src app/server/build.gradle.kts
grep -rn "environmentVariable\|System.getenv" build-logic/src app/server/build.gradle.kts
```

A `run` task with no `doFirst` that touches `environment` has the defect. To reproduce it on an
unfixed project, from a shell with no `<PREFIX>_DEV_*` variables:

```bash
./gradlew :app:server:wipeDevDatabase
<PREFIX>_DEV_SCENARIO=empty ./gradlew :app:server:run   # boots, seeds 'empty'; stop it
./gradlew :app:server:run                              # "Reusing configuration cache", then the
                                                        # seed-once failure above
```

## Migration

1. Add the scenario name to `DevDatabaseEnvironment` (or wherever the project declares the other
   two):

   ```kotlin
   /** Names the `DevScenarios` entry a brand-new cluster is seeded with. */
   const val SCENARIO: String = "<PREFIX>_DEV_SCENARIO"
   ```

2. In the `run` configuration, replace the configuration-time `environment(...)` calls with a
   `doFirst` that reads every switch through a provider declared inside the configuration action:

   ```kotlin
   plugins.withId("application") {
       tasks.named<JavaExec>("run") {
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
   ```

   The providers and the directory string are locals of the `named` action, not script-level
   properties: a `doFirst` closure that references a script-level property captures the script
   object, which the configuration cache rejects (see the `2026-07-13.1-configuration-cache`
   entry). Reading a provider at configuration time (`.get()`, `.orNull`, `.getOrElse(...)`
   outside `doFirst`) turns it back into a cache input; the reads stay inside `doFirst`.

3. Remove any `--no-configuration-cache` workaround from run configurations and scripts that
   launch the server.

## Verification

From a shell with no `<PREFIX>_DEV_*` variables:

```bash
./gradlew :app:server:wipeDevDatabase
<PREFIX>_DEV_SCENARIO=empty ./gradlew :app:server:run   # seeds 'empty'; stop it
./gradlew :app:server:run                              # "Reusing configuration cache"; boots
<PREFIX>_DEV_DB=ephemeral ./gradlew :app:server:run    # "Reusing configuration cache"; throwaway
                                                        # cluster on a random port
```

The second run prints `Reusing configuration cache.` and reaches `Responding at`. The third run
also reuses the entry and starts an ephemeral cluster.
