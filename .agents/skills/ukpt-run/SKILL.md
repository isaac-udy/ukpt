---
name: ukpt-run
description: >-
  Run or launch the app on any platform — Desktop, Android, Web, iOS, and
  Server — plus the embedded dev-Postgres and dev database setup (data location,
  env switches, wipe, seeding). Use when launching the app or working with the
  dev database.
---

# ukpt-run

Per-platform run commands and the server's dev-database setup.

Identifiers below use the template's UKPT identity (env vars like `UKPT_DEV_DB`, packages like `feature.ukpt`). In projects created from the template these are renamed to the project's own identity — check `.ukpt/template.json` for the rename map.

## Desktop

```
./gradlew :app:client:desktop:run
```

## Server (Ktor)

```
./gradlew :app:server:run
```
Boots against an embedded Postgres that keeps its data between runs; see Dev database below.

## Web (dev server)

```
./gradlew :app:client:web:wasmJsBrowserDevelopmentRun --no-configuration-cache
```
Open the served URL. The `--no-configuration-cache` flag is required (upstream `KotlinWebpack`
limitation).

## Android

Run from Android Studio, or:
```
./gradlew :app:client:android:installDebug
```
to a connected device/emulator.

## iOS

Open `app/client/ios/iosApp.xcodeproj` in Xcode and run. There is no Gradle command: the Xcode
project's "Compile Kotlin framework" build phase invokes
`:app:client:common:embedAndSignAppleFrameworkForXcode`, which builds `App.framework` and puts it
where the linker expects.

Simulator builds are **Apple Silicon only** — `:app:client:common` declares `iosArm64` +
`iosSimulatorArm64`, so the Xcode project excludes the `x86_64` simulator slice. Add an `iosX64()`
target if you need Intel Macs.

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
  random port), or anything else to connect to a real Postgres from `POSTGRES_URL` /
  `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_MAX_POOL_SIZE`. Set it in the invoking
  environment and `run` yields to you.
- `UKPT_DEV_DB_DIR` — where a persistent cluster lives; `run` points it at the build directory.
- `UKPT_DEV_SCENARIO` — names a `DevScenarios` entry to seed a **new** cluster with. Seeding is
  once-per-cluster; asking for a scenario over existing data fails rather than inserting on top.
- `PORT` — what the server listens on, default 8080.
