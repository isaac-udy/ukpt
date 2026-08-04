package com.isaacudy.ukpt

import dev.isaacudy.udytils.postgres.PostgresConfig

/**
 * Builds the [PostgresConfig] for a real (non-dev) Postgres from the environment.
 *
 * The udytils toolkit ships no app-specific defaults on purpose, so the connection vocabulary —
 * which variables, what they default to, the pool name that shows up in `pg_stat_activity` —
 * belongs to the application. The defaults below target a local Postgres; staging and production
 * set the variables.
 */
fun ukptPostgresConfigFromEnv(): PostgresConfig = PostgresConfig(
    jdbcUrl = System.getenv("POSTGRES_URL") ?: "jdbc:postgresql://localhost:5432/ukpt",
    username = System.getenv("POSTGRES_USER") ?: "dev",
    password = System.getenv("POSTGRES_PASSWORD") ?: "dev",
    maxPoolSize = System.getenv("POSTGRES_MAX_POOL_SIZE")?.toIntOrNull()
        ?: PostgresConfig.DEFAULT_MAX_POOL_SIZE,
    poolName = "ukpt-postgres",
)
