---
name: ukpt-run
description: >-
  Run the server — the development loop, the embedded dev-Postgres and dev
  database setup (data location, env switches, wipe, seeding). Use when
  launching the app or working with the dev database.
---

# ukpt-run

Identifiers here use the template's UKPT identity (`UKPT_DEV_DB`, `feature.ukpt`); projects rename these — the map is in `.ukpt/template.json`.

## Server

```
./gradlew :app:server:run
```
Open `http://localhost:8080`. The server boots against an embedded Postgres that keeps its data
between runs; see Dev database below.

The development loop is restart-based: stop the server and run it again after a change to Kotlin,
a script or a stylesheet. Static files are served with `Cache-Control: no-cache`, so a reload
after the restart picks them up. `-Pdevelopment` sets `io.ktor.development=true`.

To run it outside Gradle, `./gradlew :app:server:installDist` writes a launcher and every module jar
to `app/server/build/install/server/`; start it with `bin/server` and the same env switches. The
`ukpt-server-packaging` skill covers how those jars are named.

## Dev database

Server persistence uses the `dev.isaacudy.udytils.postgres` toolkit (Exposed + Flyway); conventions
are in [docs/serverdata.md](../../../platform/common/architecture/docs/serverdata.md) (the
`server.data.storage` section). `:platform:server:postgres` owns the Flyway migrations
(`src/main/resources/db/migration/`, empty until the first schema) and the codegen that turns them
into Exposed `Table`/`Row` sources; `:platform:server:development` owns the dev-database scenarios.

`./gradlew :app:server:run` needs no database of your own — it starts an embedded Postgres, migrates
it, seeds a brand-new one from `DefaultScenario`, and prints a banner saying where it is. The data
lives in `app/server/build/dev-postgres/pg<major>/` and survives restarts (`clean` wipes it, as does
`./gradlew :app:server:wipeDevDatabase`).

### Environment switches

- `UKPT_DEV_DB` — `embedded` (persistent, the `run` default), `ephemeral` (a throwaway cluster on a
  random port), or any other value to connect to an external Postgres via `POSTGRES_URL` /
  `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_MAX_POOL_SIZE`.
- `UKPT_DEV_DB_DIR` — where a persistent cluster lives; `run` points it at the build directory.
- `UKPT_DEV_SCENARIO` — names a `DevScenarios` entry to seed a **new** cluster with. Seeding is
  once-per-cluster; asking for a scenario over existing data fails rather than inserting on top.
- `PORT` — what the server listens on, default 8080.

`run` reads the three `UKPT_DEV_*` switches at execution time, not from the configuration-cache
entry, so a scenario set in an IDE run configuration or one shell does not carry into later runs
from another, and changing a switch reuses the cached entry.
