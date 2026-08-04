package ukpt.server

/**
 * The switches the server application reads its database configuration from.
 *
 * The names are a contract between three places that must agree: the application's `main()`, the
 * `run` task that defaults them for local development, and the fat-jar smoke test that boots the
 * deployable against a throwaway database. Declared once here so a project renaming its prefix
 * cannot leave one of the three behind.
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

    /** The variable the application takes its listening port from. */
    const val PORT: String = "PORT"
}
