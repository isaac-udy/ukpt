package com.isaacudy.ukpt

import dev.isaacudy.udytils.postgres.PostgresConfig

/**
 * Builds the [PostgresConfig] for a real (non-dev) Postgres. The udytils toolkit ships no
 * app-specific defaults, so the variable names and the defaults below — which target a local
 * Postgres — belong to the application.
 */
fun ukptPostgresConfigFromEnv(): PostgresConfig = PostgresConfig(
    jdbcUrl = System.getenv("POSTGRES_URL") ?: "jdbc:postgresql://localhost:5432/ukpt",
    username = System.getenv("POSTGRES_USER") ?: "dev",
    password = System.getenv("POSTGRES_PASSWORD") ?: "dev",
    maxPoolSize = System.getenv("POSTGRES_MAX_POOL_SIZE")?.toIntOrNull()
        ?: PostgresConfig.DEFAULT_MAX_POOL_SIZE,
    poolName = "ukpt-postgres",
)
