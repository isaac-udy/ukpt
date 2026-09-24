package com.isaacudy.ukpt

import dev.isaacudy.udytils.postgres.PostgresConfig
import dev.isaacudy.udytils.postgres.PostgresMigrator
import dev.isaacudy.udytils.postgres.buildHikariDataSource
import dev.isaacudy.udytils.postgres.embedded.DevServer
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import platform.server.development.UkptDevDatabase
import platform.server.web.installWebPlatform

fun main() {
    // Resolved before the server is built: the schema has to be migrated — and a dev database
    // booted and seeded — before anything can serve.
    val postgresConfig = resolvePostgresConfig()

    embeddedServer(Netty, port = ServerConfiguration.serverPort, host = "0.0.0.0") {
        install(Koin) {
            modules(serverDependencies(postgresConfig))
        }
        installWebPlatform(getKoin().getAll())
    }.start(wait = true)
}

/**
 * The dev path starts an embedded Postgres and migrates and seeds it itself; every other path
 * points at a real server and migrates it here, before the first request can reach it.
 */
private fun resolvePostgresConfig(): PostgresConfig {
    val devServerConfig = UkptDevDatabase.configFor(
        mode = ServerConfiguration.devDatabaseMode,
        scenarioName = ServerConfiguration.devDatabaseScenario,
        baseDirectory = ServerConfiguration.devDatabaseDirectory,
    )
    if (devServerConfig != null) return DevServer.start(devServerConfig).postgresConfig

    val postgresConfig = ServerConfiguration.postgresConfigFromEnv
    // Its own short-lived pool, closed before Koin opens the one the server serves from.
    buildHikariDataSource(postgresConfig).use { PostgresMigrator(it).migrate() }
    return postgresConfig
}
