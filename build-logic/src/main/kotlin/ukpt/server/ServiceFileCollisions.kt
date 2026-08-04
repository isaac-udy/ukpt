package ukpt.server

/**
 * One runtime-classpath entry and the `META-INF/services/…` paths it declares.
 *
 * [origin] is how the entry is named in a report — a jar file name, or a directory path.
 */
data class ServiceFileDeclaration(
    val origin: String,
    val servicePaths: Set<String>,
)

/**
 * A `META-INF/services/…` path declared by more than one classpath entry.
 *
 * A fat jar holds one file per path, so on a collision every declaration but one is dropped —
 * silently, because ServiceLoader has no way to know the file it read is a fragment. On a normal
 * classpath the same declarations coexist in their own jars and ServiceLoader reads all of them,
 * which is why a collision is invisible until something is packaged.
 */
data class ServiceFileCollision(
    val servicePath: String,
    val origins: List<String>,
    /** True when the packaged module ships its own copy of the path, which wins in the fat jar. */
    val overriddenByModuleResource: Boolean,
)

/** The outcome of [ServiceFileCollisions.analyse]: every collision, and whether any is unhandled. */
data class ServiceFileVerdict(val collisions: List<ServiceFileCollision>) {

    /** Collisions no module resource covers — the ones that will lose entries in the fat jar. */
    val unhandled: List<ServiceFileCollision> = collisions.filterNot(ServiceFileCollision::overriddenByModuleResource)

    /** Collisions a checked-in, hand-merged module resource already resolves. */
    val handled: List<ServiceFileCollision> = collisions.filter(ServiceFileCollision::overriddenByModuleResource)

    /**
     * The report text, written whether or not the check passes.
     *
     * [moduleResourceLocation] is the directory a hand-merged override belongs in, quoted so the
     * message can be acted on without first finding out where that is.
     */
    fun render(moduleResourceLocation: String): String = buildString {
        appendLine("ServiceLoader manifests on the runtime classpath")
        appendLine()
        if (collisions.isEmpty()) {
            appendLine("No META-INF/services path is declared by more than one classpath entry.")
            return@buildString
        }
        unhandled.forEach { collision ->
            appendLine("UNHANDLED ${collision.servicePath}")
            collision.origins.forEach { appendLine("    declared by $it") }
        }
        if (unhandled.isNotEmpty()) appendLine()
        handled.forEach { collision ->
            appendLine("covered   ${collision.servicePath}")
            collision.origins.forEach { appendLine("    declared by $it") }
            appendLine("    overridden by $moduleResourceLocation/${collision.servicePath}")
        }
        if (handled.isNotEmpty()) appendLine()
        if (unhandled.isNotEmpty()) appendLine(failureMessage(moduleResourceLocation))
    }

    /** The build failure text: what broke, why it is invisible without this check, and the fix. */
    fun failureMessage(moduleResourceLocation: String): String = buildString {
        appendLine(
            "${unhandled.size} ServiceLoader manifest path(s) are declared by more than one runtime " +
                "dependency. A fat jar keeps one file per path, so the losing declarations are dropped " +
                "from the deployable while every classpath-based check — run, tests, even building the " +
                "jar — keeps passing.",
        )
        appendLine()
        unhandled.forEach { collision ->
            appendLine("  ${collision.servicePath}")
            collision.origins.forEach { appendLine("    $it") }
        }
        appendLine()
        appendLine(
            "Fix: write the union of the colliding declarations to " +
                "$moduleResourceLocation/<path>. The packaged module's own resources are added to the " +
                "fat jar last and win, so the merged copy is what runtime sees. Regenerate it whenever " +
                "the declaring dependencies change version — a stale copy naming a class the new jar " +
                "dropped fails at boot with ServiceConfigurationError.",
        )
    }
}

/**
 * Finds `META-INF/services` paths that more than one runtime-classpath entry declares.
 *
 * Shadow's `mergeServiceFiles()` is meant to make this a non-issue and has been observed not to,
 * so the check is on the classpath rather than on the transformer: a collision is a hazard whether
 * or not the transformer of the day handles it.
 */
object ServiceFileCollisions {

    /** The classpath prefix ServiceLoader reads provider declarations from. */
    const val SERVICES_PREFIX: String = "META-INF/services/"

    /**
     * @param declarations every runtime-classpath entry that declares at least one service path.
     * @param moduleResourcePaths service paths the packaged module ships itself, which override
     *   anything a dependency declares at the same path.
     */
    fun analyse(
        declarations: List<ServiceFileDeclaration>,
        moduleResourcePaths: Set<String>,
    ): ServiceFileVerdict {
        val originsByPath = sortedMapOf<String, MutableList<String>>()
        declarations.forEach { declaration ->
            declaration.servicePaths.forEach { path ->
                originsByPath.getOrPut(path) { mutableListOf() } += declaration.origin
            }
        }
        return ServiceFileVerdict(
            originsByPath
                .filterValues { it.size > 1 }
                .map { (path, origins) ->
                    ServiceFileCollision(
                        servicePath = path,
                        origins = origins.sorted(),
                        overriddenByModuleResource = path in moduleResourcePaths,
                    )
                },
        )
    }
}
