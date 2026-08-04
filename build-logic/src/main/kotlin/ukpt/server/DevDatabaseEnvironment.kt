package ukpt.server

/**
 * The switches the server application reads its database configuration from.
 *
 * These names are a contract between the application's `main()`, the `run` task that defaults them,
 * and the fat-jar smoke test — declared once so a project renaming its prefix can't miss one.
 */
object DevDatabaseEnvironment {

    /** Selects a dev database; any other value means a real Postgres from `POSTGRES_*`. */
    const val MODE: String = "UKPT_DEV_DB"

    /** Where a persistent dev cluster keeps its data. */
    const val DIRECTORY: String = "UKPT_DEV_DB_DIR"

    /** A cluster that survives restarts — the default for `run`. */
    const val MODE_EMBEDDED: String = "embedded"

    /** A cluster discarded on shutdown — what a test or a one-shot boot wants. */
    const val MODE_EPHEMERAL: String = "ephemeral"

    const val PORT: String = "PORT"
}
