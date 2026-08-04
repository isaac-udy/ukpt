package com.isaacudy.ukpt

import dev.isaacudy.udytils.postgres.PostgresConfig
import dev.isaacudy.udytils.postgres.PostgresMigrator
import dev.isaacudy.udytils.postgres.buildHikariDataSource
import dev.isaacudy.udytils.postgres.embedded.DevServer
import dev.isaacudy.udytils.postgres.koin.postgresDependencies
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.koin.ktor.plugin.Koin
import platform.server.development.UkptDevDatabase
import platform.server.postgres.postgresPlatformDependencies
import java.nio.file.Path

/** `embedded` or `ephemeral` selects a dev database; anything else connects to a real Postgres. */
private const val DEV_DATABASE_MODE = "UKPT_DEV_DB"

/** Where a persistent dev cluster lives; `./gradlew :app:server:run` sets it. */
private const val DEV_DATABASE_DIRECTORY = "UKPT_DEV_DB_DIR"

/** Names a `DevScenarios` entry to seed a brand-new dev cluster with. */
private const val DEV_DATABASE_SCENARIO = "UKPT_DEV_SCENARIO"

private const val PORT = "PORT"

private const val DEFAULT_PORT = 8080

fun main() {
    // Resolved before the server is built: the schema has to be migrated — and a dev database
    // booted and seeded — before anything can serve.
    val postgresConfig = resolvePostgresConfig()

    embeddedServer(Netty, port = serverPort(), host = "0.0.0.0") {
        install(Koin) {
            modules(
                postgresDependencies(postgresConfig),
                postgresPlatformDependencies,
            )
        }
        routing {
            get("/") {
                call.respondText("Hello, ukpt server!")
            }
        }
    }.start(wait = true)
}

private fun serverPort(): Int = System.getenv(PORT)?.toIntOrNull() ?: DEFAULT_PORT

/**
 * The dev path starts an embedded Postgres and migrates and seeds it itself; every other path
 * points at a real server and migrates it here, before the first request can reach it.
 */
private fun resolvePostgresConfig(): PostgresConfig {
    val devServerConfig = UkptDevDatabase.configFor(
        mode = System.getenv(DEV_DATABASE_MODE),
        scenarioName = System.getenv(DEV_DATABASE_SCENARIO),
        baseDirectory = devDatabaseDirectory(),
    )
    if (devServerConfig != null) return DevServer.start(devServerConfig).postgresConfig

    val postgresConfig = ukptPostgresConfigFromEnv()
    // Its own short-lived pool, closed before Koin opens the one the server serves from.
    buildHikariDataSource(postgresConfig).use { PostgresMigrator(it).migrate() }
    return postgresConfig
}

private fun devDatabaseDirectory(): Path =
    System.getenv(DEV_DATABASE_DIRECTORY)
        ?.takeIf { it.isNotBlank() }
        ?.let { Path.of(it) }
        ?: Path.of("build", "dev-postgres")
