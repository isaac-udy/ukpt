---
name: ukpt-server-packaging
description: >-
  Build, verify, and deploy the server fat jar — buildFatJar, runtime
  service-file collision checks, Shadow 9.1.0's mergeServiceFiles() silently not
  merging, smokeTestFatJar, and the installDist development distribution and its
  archive naming. Use when packaging or deploying the server.
---

# ukpt-server-packaging

Identifiers here use the template's UKPT identity (`UKPT_DEV_DB`); projects rename these — the map is in `.ukpt/template.json`.

`./gradlew :app:server:buildFatJar` builds the deployable, minus the dev database: Zonky's embedded
Postgres, its per-platform binaries and `:platform:server:development` are filtered out of the
Shadow jar (`ukpt.server-packaging`). `run` and the tests are unaffected — they use the normal
runtime classpath.

## Service-file collision check

`verifyRuntimeServiceFiles` (part of `check`, and gates `shadowJar`) fails when two runtime
dependencies declare the same `META-INF/services` path, since only one copy survives packaging.
`flyway-core` and `flyway-database-postgresql` collide;
`app/server/src/main/resources/META-INF/services/` holds a hand-merged copy (its README says when to
regenerate it).

**Do not trust Shadow's `mergeServiceFiles()`**: it is called and, on 9.1.0, does not merge —
verify by extracting the file from a built jar.

## Smoke test

```
./gradlew :app:server:smokeTestFatJar
```
Boots the built jar the way a container would, on an OS-assigned port against a throwaway database,
and asserts it migrates and answers. Nothing else exercises the jar itself.

## Development distribution

```
./gradlew :app:server:installDist
UKPT_DEV_DB=ephemeral app/server/build/install/server/bin/server
```
Unlike the fat jar this keeps every module as its own file under `lib/`, and it keeps the dev
database. It is the "run the server outside Gradle" path, not the deployable.

Module jars are named after the full project path (`feature-core-server.jar`,
`platform-server-postgres.jar`), set by `ukpt.jvm-base` and `ukpt.kmp-library` through
`ukpt.ArchiveNaming`. Gradle's default name is the leaf directory, which a project laid out by
feature repeats: every `:*:server` module produces `server.jar`, and copying them into one `lib/`
fails on the duplicate entry. A `duplicatesStrategy` on `installDist` is not the fix — the
colliding files are different modules, so whichever strategy applies drops a feature's classes.

The fat jar keeps the name Ktor's `buildFatJar` gives it (`server-all.jar`), which it pins
independently of `archivesName`.
