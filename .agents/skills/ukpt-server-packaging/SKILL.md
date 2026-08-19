---
name: ukpt-server-packaging
description: >-
  Build, verify, and deploy the server fat jar — buildFatJar, runtime
  service-file collision checks, Shadow 9.1.0's mergeServiceFiles() silently not
  merging, and smokeTestFatJar. Use when packaging or deploying the server.
---

# ukpt-server-packaging

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
