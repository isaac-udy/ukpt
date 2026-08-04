package ukpt.server

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.specs.Spec

/**
 * The dependencies that exist only so a developer can run the server against a throwaway database.
 *
 * One list, two consumers that have to agree: the fat jar leaves this subgraph out, and
 * `smokeTestFatJar` puts exactly it back on the classpath to boot that jar. If the two ever named
 * different things, the smoke test would either fail to start Postgres or quietly re-supply
 * something the deployable is missing — so neither names anything itself.
 *
 * It is a subgraph by coordinate, not by transitive reach: Shadow judges each dependency on its
 * own, so a library shared with production code (commons-compress, slf4j) stays in the jar even
 * though the embedded server is what dragged it in.
 */
object DevDatabaseSubgraph {

    /**
     * Module coordinates in Shadow's `dependency(...)` notation, where each segment is a regex.
     *
     * `io.zonky.test.postgres` is one artifact per platform, each carrying a ~50 MB Postgres
     * distribution; the group is matched whole because a project adds and drops those per target
     * platform and none of them may ever reach a deployable.
     */
    val moduleNotations: List<String> = listOf(
        "io.zonky.test:embedded-postgres",
        "io.zonky.test.postgres:.*",
        "dev.isaacudy.udytils:postgres-embedded",
    )

    /** Project paths whose classes are development-only. */
    val projectPaths: List<String> = listOf(
        ":platform:server:development",
    )

    /** Jar-entry markers that must not appear in a deployable, checked by `smokeTestFatJar`. */
    val forbiddenJarEntryMarkers: List<String> = listOf(
        "io/zonky/",
        "platform/server/development/",
    )

    /** A module name that is spelled out rather than matched — see [substitutedProjectNames]. */
    private val literalName = Regex("[A-Za-z0-9._-]+")

    private val modulePatterns: List<Pair<Regex, Regex>> = moduleNotations.map { notation ->
        Regex(notation.substringBefore(':')) to Regex(notation.substringAfter(':'))
    }

    /**
     * The module names above that a composite build can substitute for a project of its own.
     *
     * A dependency substituted by an `includeBuild` resolves to a project component, not a module
     * one, so its coordinates are gone by the time a classpath is resolved — only the project name
     * survives, which the substituted builds keep equal to the artifact name they publish. Wildcard
     * names are left out: they exist to sweep up a family of published artifacts and would match
     * every project in the build tree.
     */
    private val substitutedProjectNames: List<String> =
        moduleNotations.map { it.substringAfter(':') }.filter(literalName::matches)

    /** Selects exactly this subgraph out of a resolved classpath, for the smoke test's `-cp`. */
    fun componentFilter(): Spec<ComponentIdentifier> = Spec { identifier ->
        when (identifier) {
            is ModuleComponentIdentifier -> modulePatterns.any { (group, name) ->
                group.matches(identifier.group) && name.matches(identifier.module)
            }

            is ProjectComponentIdentifier ->
                identifier.projectPath in projectPaths || identifier.projectName in substitutedProjectNames

            else -> false
        }
    }
}
