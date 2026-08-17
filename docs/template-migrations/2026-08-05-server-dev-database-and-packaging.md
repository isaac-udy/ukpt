# The server gets a dev database, and a deployable that is checked

The template previously described Postgres persistence and shipped none of it: no
`:platform:server:postgres`, no dev database, a `Server.kt` with no DI host, and a fat jar nobody
had opened. It now ships all four, plus the two build checks that a fat jar turns out to need.

What landed:

- **`:platform:server:postgres`** — the Flyway migrations (`src/main/resources/db/migration/`,
  empty until the project has a schema), the `dev.isaacudy.udytils.postgres` codegen plugin that
  turns them into Exposed `Table`/`Row` sources in `platform.server.postgres.tables`, the committed
  `schema.sql` snapshot, and `TransactionRunner` bound in `postgresPlatformDependencies`.
- **`:platform:server:development`** — `DevScenario` seed objects and `UkptDevDatabase`, which
  turns the environment's switches into a `DevServerConfig`. The only module allowed to depend on
  `postgres-embedded`, because that is what drags in Zonky's Postgres binaries.
- **An environment contract**: `<PREFIX>_DEV_DB` (`embedded` / `ephemeral` / anything else for a
  real server), `<PREFIX>_DEV_DB_DIR`, `<PREFIX>_DEV_SCENARIO`, and `PORT`. The `ukpt.dev-database`
  convention plugin defaults the first two on `run` — only when the invoking environment is silent
  — and adds `wipeDevDatabase`. The names are declared once, in `ukpt.server.DevDatabaseEnvironment`
  in build-logic, because `main()` and the `run` task have to agree on them.
- **A Koin host in `Server.kt`**: `install(Koin) { modules(postgresDependencies(config),
  postgresPlatformDependencies) }`, with the config resolved *before* the server is built, so
  migration and seeding finish before anything can serve a request.
- **`ukpt.server-packaging`**: the dev-database subgraph is excluded from the Shadow jar,
  `verifyRuntimeServiceFiles` fails on colliding `META-INF/services` paths, and `smokeTestFatJar`
  boots the built jar against a throwaway database.
- **The rename planner** now renames the all-caps environment-variable prefix, including in
  `build-logic/src/main/` — see "Environment-variable prefix" below.

## Detection

Every project with a `:app:server` is affected; how much depends on what it already hand-rolled.

```bash
ls platform/server/postgres platform/server/development 2>/dev/null   # modules present?
grep -rn "DEV_DB\|DevServer\.start" app/server/src build-logic/src    # dev database wired?
grep -rn "install(Koin)" app/server/src                               # DI host present?
grep -rn "mergeServiceFiles\|shadowJar" build-logic/src app/server/build.gradle.kts
ls app/server/src/main/resources/META-INF/services 2>/dev/null        # merged manifest present?
```

Reglyph and Leegaa both already have an embedded dev database and a Koin host; Leegaa also has the
hand-merged Flyway manifest. Neither has the build checks or the fat-jar exclusions. **Keep** what
a project already has working and take only the parts it lacks — the sections below are
independent.

## Migration

### 1. The persistence modules

Only for a project that has no `:platform:server:postgres`. Copy both module directories from the
template, add the two `include(...)` lines to `settings.gradle.kts`, add the
`udytils-postgres-{core,koin,embedded,gradlePlugin}` catalog entries and the four matching
`substitute(...)` lines to the `embedded-udytils` `includeBuild` block, and put
`classpath(libs.udytils.postgres.gradlePlugin)` in the root `buildscript`. The plugin has to come
from the root buildscript classpath rather than `pluginManagement`: a top-level `includeBuild` in
`pluginManagement` silently drops the dependency substitutions.

Rename the `platform.server.*` packages only if the project renamed its platform modules; the
generated tables' package (`outputPackage`) is referenced by every feature's storage code, so
changing it is a project-wide edit.

A project that already has these modules under different names keeps them. What it should take is
`DevScenarios.byName` throwing on an unknown name, if it does not already — an operator who
misspells a scenario wants to be told, not handed a different starting state.

### 2. The environment contract

If the project already reads its own `<PREFIX>_DEV_DB`-style variables, keep them; only make sure
the names are declared in **one** place that both build-logic and application code use — on the
application side, `ServerConfiguration` in `:app:server` — and that `main()` reads `PORT` (default
8080) rather than hard-coding a port — the fat-jar smoke test needs to boot without colliding with
a running dev server, and container runtimes assign a port anyway.

Otherwise take `ukpt.dev-database.gradle.kts` and `ukpt/server/DevDatabaseEnvironment.kt` from
build-logic, apply the plugin to `:app:server`, and substitute the project's own prefix.

### 3. Fat-jar exclusions

Take `ukpt.server-packaging.gradle.kts` and `ukpt/server/DevDatabaseSubgraph.kt`, apply the plugin
to `:app:server`, and edit `DevDatabaseSubgraph` so `projectPaths` names the project's own
development module. `moduleNotations` should not need changing unless the project pulls the Zonky
binaries under other coordinates.

build-logic needs `libs.ktor.gradlePlugin` and `libs.shadow.gradlePlugin` on its classpath to
compile against the `ShadowJar` type. Ktor publishes its plugin as `io.ktor.plugin:plugin` and
keeps Shadow off its compile metadata, so Shadow is a separate catalog entry whose version must
equal what Ktor brings (`./gradlew :app:server:buildEnvironment | grep shadow`).

Verify by listing the jar, not by trusting the DSL:

```bash
./gradlew :app:server:buildFatJar
unzip -l app/server/build/libs/*-all.jar | grep -c zonky   # must be 0
```

### 4. The ServiceLoader check, and the Flyway manifest

Take `ukpt/server/ServiceFileCollisions.kt`, `VerifyRuntimeServiceFilesTask.kt` and their unit
test. `ukpt.server-packaging` wires the task into `check` and makes `shadowJar` depend on it.

The first run will fail on `META-INF/services/org.flywaydb.core.extensibility.Plugin`, which
`flyway-core` and `flyway-database-postgresql` both declare with disjoint contents. A fat jar keeps
one file per path, so one of them is dropped — and Flyway then either throws an NPE while being
configured or finds zero migrations and reports success, leaving production's schema empty.
`mergeServiceFiles()` is supposed to fix this and **does not** on Shadow 9.1.0; the template calls
it anyway and does not rely on it.

A project that already has `app/server/src/main/resources/META-INF/services/` (Leegaa) needs
nothing here — the check will report the collision as covered. A project without one generates it
per the README beside the template's copy, and copies that README too: the file goes stale when the
resolved Flyway version changes, and nothing else says so.

### 5. The boot smoke test

Take `ukpt/server/SmokeTestFatJarTask.kt`. Two things about it are load-bearing and easy to undo:

- The classpath is the fat jar **plus exactly the artifacts the jar excluded**, resolved through an
  `artifactView` using the same `DevDatabaseSubgraph` predicate as the exclusions. Adding the whole
  runtime classpath instead would put a second, intact `flyway-core` in front of ServiceLoader and
  the test would pass over a broken jar.
- The asserted log fragments (`Flyway migration complete:`, `Dev database: embedded-ephemeral`) are
  what distinguishes "migrated" from "reported success having found nothing". An HTTP 200 alone
  does not: the silent failure mode also returns 200.

If the project has a CI workflow, add `:app:server:smokeTestFatJar` to it. It is deliberately not
part of `check` — it boots a server and an embedded Postgres.

### 6. Environment-variable prefix in a rename

`ProjectRenamePlanner` matches the all-caps `UKPT` inside a SCREAMING_SNAKE token and replaces it
with the project name uppercased (`leegaa` → `LEEGAA_DEV_DB`). This is the one exception to
`build-logic/` being protected: the application reads those variables and a convention plugin
defaults them, so renaming one side alone breaks `run`. build-logic's *tests* stay protected —
their fixtures are the planner's subject matter. A bare `UKPT` (prose, `UKPT.md`) is not matched at
all.

An already-renamed project is unaffected — it renamed before these variables existed, so there is
nothing stale to find. Projects created from this version onwards get it for free.

## Verification

```bash
./gradlew -p build-logic test
./gradlew :app:server:test :platform:server:postgres:test
./gradlew :app:server:verifyRuntimeServiceFiles
./gradlew :app:server:smokeTestFatJar
./gradlew :app:server:run            # banner names the dev cluster; ^C
```

`smokeTestFatJar` passing is the one that matters: it is the only check that runs the artifact the
project actually deploys.
