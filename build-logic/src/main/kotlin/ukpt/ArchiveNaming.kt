package ukpt

/**
 * Derives a module's `archivesName` from its full Gradle path.
 *
 * Gradle's default is the leaf directory, which repeats across a project laid out by feature:
 * `:app:server`, `:feature:core:server` and every other `:feature:*:server` would all produce
 * `server.jar`, and every `:feature:*:api` an `api-jvm.jar`. Any task that gathers the runtime
 * classpath into one directory — `installDist` — then fails on the duplicate entry.
 */
object ArchiveNaming {
    fun baseNameFor(projectPath: String): String =
        projectPath.trim(':')
            .replace(':', '-')
            .ifEmpty { "root" }
}
