package architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Validates the §2 Gradle-module dependency rules at build-script level.
 *
 * The rest of the architecture suite catches violations at *import* time (a
 * file imports a forbidden symbol). These tests catch violations one layer
 * deeper: a `build.gradle.kts` that *declares* a forbidden module dependency,
 * even before any source file uses it.
 *
 * Walks every `build.gradle.kts` under the project root (excluding
 * `embedded-*` submodules), parses `projects.X.Y.Z` typesafe accessors, and
 * checks each declared dependency against the rules:
 *
 *  * R-MOD-01: `:feature` → `:app` forbidden
 *  * R-MOD-03: `:feature:[name]:client` → other `:client` / `:server` forbidden
 *  * R-MOD-05: `:feature:[name]:server` → other `:client` / `:server` forbidden
 *  * R-MOD-09: `:platform` → `:app` forbidden
 *  * R-MOD-10: `:platform` → `:feature` forbidden
 */
class ModuleDependencyTests {

    private val projectRoot: File = File(".").canonicalFile.let { dir ->
        // Tests run from `:platform:common:architecture` working dir. Walk up
        // until we find settings.gradle.kts (the project root marker).
        var current: File? = dir
        while (current != null && !File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile
        }
        requireNotNull(current) { "Could not locate project root from ${dir.path}" }
    }

    private val buildFiles: List<File> by lazy {
        projectRoot.walkTopDown()
            .filter { it.name == "build.gradle.kts" }
            .filter { !it.absolutePath.contains("/embedded-") }
            .filter { !it.absolutePath.contains("/build/") }
            .filter { !it.absolutePath.contains("/.gradle/") }
            .filter { it.parentFile != projectRoot } // skip root build.gradle.kts
            .toList()
    }

    private val typesafeProjectAccessor = Regex("""\bprojects(?:\.[a-zA-Z][a-zA-Z0-9]*)+""")

    /**
     * Per-line architecture-exception marker for `build.gradle.kts` files.
     * Build scripts can't carry the `@ArchitectureException` annotation (no
     * compile classpath), so each gradle dependency line may instead be
     * preceded by:
     *
     *   // architecture-exception: R-MOD-10
     *
     * The line containing the dependency is exempt from any rule listed.
     */
    private val exceptionCommentRegex = Regex("""//\s*architecture-exception:\s*([A-Z0-9\-,\s]+)""")

    /**
     * Convert `projects.platform.common.textSimilarity` to `:platform:common:text-similarity`.
     */
    private fun accessorToModulePath(accessor: String): String {
        val segments = accessor.removePrefix("projects.").split(".")
        return segments.joinToString(":", prefix = ":") { camelToKebab(it) }
    }

    private fun camelToKebab(name: String): String {
        return name.fold(StringBuilder()) { acc, c ->
            if (c.isUpperCase() && acc.isNotEmpty()) acc.append('-')
            acc.append(c.lowercaseChar())
        }.toString()
    }

    /**
     * Convert a `build.gradle.kts` file path to its gradle module path.
     * Example: `<root>/feature/core/server/build.gradle.kts` → `:feature:core:server`.
     */
    private fun File.toModulePath(): String {
        val rel = this.parentFile.relativeTo(projectRoot).invariantSeparatorsPath
        return ":" + rel.replace('/', ':')
    }

    /** A `projects.X.Y.Z` reference paired with the rule IDs it's exempt from. */
    private data class ParsedDependency(val modulePath: String, val exemptRuleIds: Set<String>)

    private fun parsedDependencies(file: File): List<ParsedDependency> {
        val lines = file.readLines()
        val results = mutableListOf<ParsedDependency>()
        for ((index, line) in lines.withIndex()) {
            for (match in typesafeProjectAccessor.findAll(line)) {
                val modulePath = accessorToModulePath(match.value)
                val exempt = collectExemptionsFor(lines, index)
                results += ParsedDependency(modulePath, exempt)
            }
        }
        return results.distinct()
    }

    /**
     * Walk back from [index] to gather any `// architecture-exception:` rule
     * IDs that apply to the line at [index]. Multiple comment lines may
     * stack; the walk stops at the first non-comment / non-blank line.
     */
    private fun collectExemptionsFor(lines: List<String>, index: Int): Set<String> {
        val ids = mutableSetOf<String>()
        var i = index - 1
        while (i >= 0) {
            val trimmed = lines[i].trim()
            // Allow blank lines? No — exemption must be immediately above.
            if (trimmed.isEmpty()) break
            if (!trimmed.startsWith("//")) break
            val match = exceptionCommentRegex.find(trimmed)
            if (match != null) {
                match.groupValues[1].split(',').forEach { id ->
                    val trimmedId = id.trim()
                    if (trimmedId.isNotEmpty()) ids.add(trimmedId)
                }
            }
            i--
        }
        return ids
    }

    private fun isAppModule(path: String) = path.startsWith(":app:") || path == ":app"
    private fun isFeatureModule(path: String) = path.startsWith(":feature:") || path == ":feature"
    private fun isPlatformModule(path: String) = path.startsWith(":platform:") || path == ":platform"

    /**
     * `:feature:[name]:[type]` → `client` / `server` / `api`. Returns null
     * if the path doesn't match the expected shape.
     */
    private fun featureSubmoduleType(path: String): String? {
        if (!isFeatureModule(path)) return null
        val segments = path.removePrefix(":").split(":")
        // `:feature:core:server` → segments = [feature, core, server]
        return segments.lastOrNull()?.takeIf { it in setOf("api", "client", "server") }
    }

    private fun reportViolations(violations: List<String>, ruleId: String) {
        if (violations.isEmpty()) return
        fail(
            buildString {
                append("[$ruleId] forbidden module dependencies:\n")
                violations.forEach { append("  - ").append(it).append('\n') }
            }
        )
    }

    /**
     * Enforces R-MOD-01 (§2.1): `:feature` modules must never depend on `:app`.
     */
    @Test
    fun `R-MOD-01 feature modules must not depend on app modules`() {
        val violations = mutableListOf<String>()
        buildFiles
            .map { it to it.toModulePath() }
            .filter { (_, path) -> isFeatureModule(path) }
            .forEach { (file, path) ->
                parsedDependencies(file)
                    .filter { dep -> isAppModule(dep.modulePath) }
                    .filterNot { dep -> "R-MOD-01" in dep.exemptRuleIds }
                    .forEach { dep ->
                        violations.add("$path → ${dep.modulePath} (in ${file.name})")
                    }
            }
        reportViolations(violations, "R-MOD-01")
    }

    /**
     * Enforces R-MOD-03 / R-MOD-05 (§2.1): `:feature:[name]:client` and
     * `:feature:[name]:server` must never depend on other `:client` or
     * `:server` modules. They may only depend on `:feature:[name]:api` and
     * `:platform`. Same-feature `:api` is the only feature-to-feature path.
     */
    @Test
    fun `R-MOD-03 R-MOD-05 feature client server must only depend on api or platform`() {
        val violations = mutableListOf<String>()
        buildFiles
            .map { it to it.toModulePath() }
            .filter { (_, path) -> featureSubmoduleType(path) in setOf("client", "server") }
            .forEach { (file, path) ->
                val ruleId = if (featureSubmoduleType(path) == "client") "R-MOD-03" else "R-MOD-05"
                parsedDependencies(file).forEach { dep ->
                    val depPath = dep.modulePath
                    val isViolation = when {
                        isPlatformModule(depPath) -> false
                        !isFeatureModule(depPath) -> false
                        featureSubmoduleType(depPath) == "api" -> false
                        else -> true
                    }
                    if (isViolation && ruleId !in dep.exemptRuleIds) {
                        violations.add("$path → $depPath (in ${file.name})")
                    }
                }
            }
        reportViolations(violations, "R-MOD-03 / R-MOD-05")
    }

    /**
     * Enforces R-MOD-09 (§2.2): `:platform` modules must never depend on `:app`.
     */
    @Test
    fun `R-MOD-09 platform modules must not depend on app modules`() {
        val violations = mutableListOf<String>()
        buildFiles
            .map { it to it.toModulePath() }
            .filter { (_, path) -> isPlatformModule(path) }
            .forEach { (file, path) ->
                parsedDependencies(file)
                    .filter { dep -> isAppModule(dep.modulePath) }
                    .filterNot { dep -> "R-MOD-09" in dep.exemptRuleIds }
                    .forEach { dep ->
                        violations.add("$path → ${dep.modulePath} (in ${file.name})")
                    }
            }
        reportViolations(violations, "R-MOD-09")
    }

    /**
     * Enforces R-MOD-10 (§2.2): `:platform` modules must never depend on `:feature`.
     */
    @Test
    fun `R-MOD-10 platform modules must not depend on feature modules`() {
        val violations = mutableListOf<String>()
        buildFiles
            .map { it to it.toModulePath() }
            .filter { (_, path) -> isPlatformModule(path) }
            .forEach { (file, path) ->
                parsedDependencies(file)
                    .filter { dep -> isFeatureModule(dep.modulePath) }
                    .filterNot { dep -> "R-MOD-10" in dep.exemptRuleIds }
                    .forEach { dep ->
                        violations.add("$path → ${dep.modulePath} (in ${file.name})")
                    }
            }
        reportViolations(violations, "R-MOD-10")
    }
}
