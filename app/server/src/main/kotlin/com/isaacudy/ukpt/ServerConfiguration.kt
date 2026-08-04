package com.isaacudy.ukpt

import dev.isaacudy.udytils.postgres.PostgresConfig
import java.nio.file.Path

/**
 * Everything the server reads from its environment. The `DEV_DATABASE_*` names must stay
 * identical to `ukpt.server.DevDatabaseEnvironment` in build-logic — application code can't
 * import build-logic, so the two are kept in sync by convention (the rename planner rewrites
 * both).
 */
internal object ServerConfiguration {

    private const val DEV_DATABASE_MODE = "UKPT_DEV_DB"

    private const val DEV_DATABASE_DIRECTORY = "UKPT_DEV_DB_DIR"

    private const val DEV_DATABASE_SCENARIO = "UKPT_DEV_SCENARIO"

    private const val PORT = "PORT"

    private const val DEFAULT_PORT = 8080

    val devDatabaseMode: String? get() = System.getenv(DEV_DATABASE_MODE)

    val devDatabaseScenario: String? get() = System.getenv(DEV_DATABASE_SCENARIO)

    val devDatabaseDirectory: Path
        get() = System.getenv(DEV_DATABASE_DIRECTORY)
            ?.takeIf { it.isNotBlank() }
            ?.let { Path.of(it) }
            ?: Path.of("build", "dev-postgres")

    val serverPort: Int get() = System.getenv(PORT)?.toIntOrNull() ?: DEFAULT_PORT

    /**
     * Builds the [PostgresConfig] for a real (non-dev) Postgres. The udytils toolkit ships no
     * app-specific defaults, so the variable names and the defaults below — which target a local
     * Postgres — belong to the application.
     */
    val postgresConfigFromEnv: PostgresConfig
        get() = PostgresConfig(
            jdbcUrl = System.getenv("POSTGRES_URL") ?: "jdbc:postgresql://localhost:5432/ukpt",
            username = System.getenv("POSTGRES_USER") ?: "dev",
            password = System.getenv("POSTGRES_PASSWORD") ?: "dev",
            maxPoolSize = System.getenv("POSTGRES_MAX_POOL_SIZE")?.toIntOrNull()
                ?: PostgresConfig.DEFAULT_MAX_POOL_SIZE,
            poolName = "ukpt-postgres",
        )
}
