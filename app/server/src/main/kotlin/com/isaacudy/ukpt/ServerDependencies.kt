package com.isaacudy.ukpt

import dev.isaacudy.udytils.postgres.PostgresConfig
import dev.isaacudy.udytils.postgres.koin.postgresDependencies
import org.koin.core.module.Module
import platform.server.postgres.postgresPlatformDependencies

/**
 * The server's whole dependency graph. Each feature's `[name]ServerDependencies` is added here,
 * so `ServerDependenciesTest` verifies the same list the server installs.
 */
internal fun serverDependencies(postgresConfig: PostgresConfig): List<Module> = listOf(
    postgresDependencies(postgresConfig),
    postgresPlatformDependencies,
)
