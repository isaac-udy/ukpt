# Why this directory exists

`org.flywaydb.core.extensibility.Plugin` is a hand-merged ServiceLoader manifest. `flyway-core`
and `flyway-database-postgresql` both ship one at that path with disjoint contents — 37 core
entries and 3 Postgres ones — and Flyway assembles itself from the union.

A fat jar can only hold one file per path, and `mergeServiceFiles()` (which `ukpt.server-packaging`
calls) does not merge under Shadow 9.1.0 — the Postgres module's 3 entries silently overwrite
core's 37. Without the core plugins (`CoreMigrationResolver`, `CoreResourceTypeProvider`, …) Flyway
either throws an NPE while its configuration is being built, or runs, finds zero migrations and
reports success — leaving the production schema empty while every local run works.

This file is the union of the two. The packaged module's own resources are added to the fat jar
last and win the conflict, so this copy is the one runtime sees.

## When to update

Regenerate when the Flyway version changes — including through the `embedded-udytils` submodule,
whose pin wins conflict resolution over this repository's own catalog. A stale copy fails at boot
with `ServiceConfigurationError: Provider <class> not found` the moment a new `flyway-core` drops
a class this file still names.

Set `V` to the version that actually resolves; a bare `*/*.jar` glob would merge every version in
the Gradle cache and reintroduce exactly that failure:

```sh
V=$(./gradlew :app:server:dependencies --configuration runtimeClasspath | \
    grep -o 'org.flywaydb:flyway-core:[0-9.]*' | head -1 | cut -d: -f3)
F=~/.gradle/caches/modules-2/files-2.1/org.flywaydb
{
  unzip -p "$F"/flyway-core/$V/*/flyway-core-$V.jar META-INF/services/org.flywaydb.core.extensibility.Plugin
  echo
  unzip -p "$F"/flyway-database-postgresql/$V/*/flyway-database-postgresql-$V.jar META-INF/services/org.flywaydb.core.extensibility.Plugin
} | grep -v '^$' | sort -u > app/server/src/main/resources/META-INF/services/org.flywaydb.core.extensibility.Plugin
```

`verifyRuntimeServiceFiles` reports a collision this directory covers instead of failing on it, so
deleting a file here without deleting the collision turns the build red rather than shipping a
broken jar. Delete the directory if Shadow's transformer is ever verified fixed — extract the path
from a jar built without these resources and confirm it holds both artifacts' entries.
