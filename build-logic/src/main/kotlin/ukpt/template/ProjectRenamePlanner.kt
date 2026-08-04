package ukpt.template

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * The replacement identity supplied when turning a fresh UKPT checkout into a named project.
 *
 * The project name is a lowercase slug, the package name is a dotted JVM package, and the type
 * prefix is the PascalCase stem used for project-branded Kotlin types.
 */
data class ProjectRenameRequest(
    val projectName: String,
    val packageName: String,
    val typePrefix: String,
)

/** Describes how a matched UKPT identity token should be handled during a project rename. */
enum class RenameDisposition {
    /** The token is project-owned and must be replaced. */
    REPLACE,

    /** The correct replacement depends on a project-specific choice. */
    REVIEW,

    /** The token belongs to stable template infrastructure and must remain unchanged. */
    KEEP,
}

/** A single identity token found in the repository, including its proposed replacement and action. */
data class RenameOccurrence(
    val path: String,
    val line: Int,
    val column: Int,
    val source: String,
    val replacement: String,
    val disposition: RenameDisposition,
    val reason: String,
)

/** A package directory that must move so its path continues to match the renamed package. */
data class DirectoryMove(
    val source: String,
    val destination: String,
)

/**
 * The complete, non-mutating inventory produced for a project rename.
 *
 * It contains both content replacements and package-directory moves so an agent can review the
 * rename before editing any files.
 */
data class ProjectRenamePlan(
    val request: ProjectRenameRequest,
    val occurrences: List<RenameOccurrence>,
    val directoryMoves: List<DirectoryMove>,
) {
    /** Renders the plan as the deterministic, human-readable report written by the Gradle task. */
    fun render(): String = buildString {
        appendLine("UKPT project rename plan")
        appendLine("project name: ${request.projectName}")
        appendLine("package:      ${request.packageName}")
        appendLine("type prefix:  ${request.typePrefix}")
        appendLine()
        appendLine("Directory moves")
        if (directoryMoves.isEmpty()) appendLine("  (none)")
        directoryMoves.forEach { appendLine("  ${it.source} -> ${it.destination}") }
        appendLine()
        RenameDisposition.entries.forEach { disposition ->
            val matching = occurrences.filter { it.disposition == disposition }
            appendLine("$disposition (${matching.size})")
            if (matching.isEmpty()) appendLine("  (none)")
            if (disposition == RenameDisposition.KEEP) {
                matching.groupingBy(RenameOccurrence::path).eachCount().forEach { (path, count) ->
                    appendLine("  $path ($count protected occurrence(s))")
                }
            } else {
                matching.forEach { occurrence ->
                    appendLine(
                        "  ${occurrence.path}:${occurrence.line}:${occurrence.column} " +
                            "'${occurrence.source}' -> '${occurrence.replacement}' — ${occurrence.reason}",
                    )
                }
            }
            appendLine()
        }
    }
}

/**
 * Scans a UKPT checkout and builds a safe project-rename inventory without modifying the checkout.
 *
 * Matches are classified as required replacements, project-specific review items, or protected
 * template identifiers. Generated output, embedded repositories, and other non-source trees are
 * excluded from the scan.
 */
object ProjectRenamePlanner {
    private val projectNamePattern = Regex("^[a-z][a-z0-9-]*$")
    private val packagePattern = Regex("^[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+$")
    private val typePrefixPattern = Regex("^[A-Z][A-Za-z0-9]*$")
    private val identityPattern = Regex(
        // The all-caps form is only ever the environment-variable prefix, so it is matched only
        // where one appears — inside a SCREAMING_SNAKE token. A bare `UKPT` is prose or a file name.
        "com\\.isaacudy\\.ukpt|feature\\.ukpt|UKPT(?=_[A-Z0-9])|Ukpt(?=[A-Z]|\\b)|ukpt(?=[A-Z]|\\b)",
    )
    private val skippedDirectories = setOf(
        ".git",
        ".gradle",
        ".idea",
        ".kotlin",
        ".cxx",
        ".externalNativeBuild",
        "build",
        "captures",
        "node_modules",
        "embedded-enro",
        "embedded-udytils",
    )
    private val protectedPrefixes = listOf(
        ".agents/",
        ".claude/",
        ".ukpt/",
        "build-logic/",
        "docs/template-migrations/",
        "platform/common/architecture/",
    )
    private val workedCoreIdentifiers = setOf(
        "UkptDestination",
        "ukptClientDependencies",
    )

    /** Returns every validation error in [request], or an empty list when it is safe to plan. */
    fun validate(request: ProjectRenameRequest): List<String> = buildList {
        if (!projectNamePattern.matches(request.projectName)) {
            add("project name must be a lowercase slug such as my-project")
        }
        if (!packagePattern.matches(request.packageName)) {
            add("package must contain at least two lowercase Java/Kotlin segments")
        }
        if (!typePrefixPattern.matches(request.typePrefix)) {
            add("type prefix must be a PascalCase identifier such as MyProject")
        }
    }

    /**
     * Builds a deterministic rename plan for [repository].
     *
     * @throws IllegalArgumentException when [request] contains an unsafe identity value.
     */
    fun plan(repository: Path, request: ProjectRenameRequest): ProjectRenamePlan {
        val validationErrors = validate(request)
        require(validationErrors.isEmpty()) { validationErrors.joinToString("; ") }

        val occurrences = mutableListOf<RenameOccurrence>()
        val directoryMoves = mutableListOf<DirectoryMove>()
        Files.walkFileTree(repository, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(directory: Path, attributes: BasicFileAttributes): FileVisitResult {
                if (directory != repository && directory.name in skippedDirectories) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                val relativePath = repository.relativize(directory).invariantSeparatorsPathString
                if (relativePath.endsWith("/com/isaacudy/ukpt")) {
                    val parent = relativePath.removeSuffix("/com/isaacudy/ukpt")
                    directoryMoves += DirectoryMove(
                        source = relativePath,
                        destination = "$parent/${request.packageName.replace('.', '/')}",
                    )
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                if (attributes.isRegularFile && attributes.size() <= 1_048_576 && !Files.isSymbolicLink(file)) {
                    collectOccurrences(repository, file, request, occurrences)
                }
                return FileVisitResult.CONTINUE
            }
        })

        return ProjectRenamePlan(
            request = request,
            occurrences = occurrences.sortedWith(
                compareBy<RenameOccurrence>({ it.disposition }, { it.path }, { it.line }, { it.column }),
            ),
            directoryMoves = directoryMoves.distinct().sortedBy(DirectoryMove::source),
        )
    }

    private fun collectOccurrences(
        repository: Path,
        file: Path,
        request: ProjectRenameRequest,
        destination: MutableList<RenameOccurrence>,
    ) {
        val contents = try {
            file.readText(StandardCharsets.UTF_8)
        } catch (_: IOException) {
            // Unreadable or non-UTF-8 (CharacterCodingException) files carry no occurrences.
            return
        }
        if ('\u0000' in contents) return

        val relativePath = repository.relativize(file).invariantSeparatorsPathString
        contents.lineSequence().forEachIndexed { lineIndex, line ->
            identityPattern.findAll(line).forEach { match ->
                val source = match.value
                val (disposition, reason) = classify(relativePath, line, match.range.first, source)
                destination += RenameOccurrence(
                    path = relativePath,
                    line = lineIndex + 1,
                    column = match.range.first + 1,
                    source = source,
                    replacement = replacementFor(source, line, match.range.first, request),
                    disposition = disposition,
                    reason = reason,
                )
            }
        }
    }

    private fun classify(
        path: String,
        line: String,
        column: Int,
        source: String,
    ): Pair<RenameDisposition, String> {
        if (source == "UKPT") return classifyEnvironmentPrefix(path)
        if (path == "UKPT.md" || protectedPrefixes.any(path::startsWith)) {
            return RenameDisposition.KEEP to "template identity is protected"
        }
        if (source == "ukpt" && line.substring(column).startsWith("ukpt.")) {
            return RenameDisposition.KEEP to "UKPT Gradle property and convention-plugin keys are stable"
        }
        if (source == "feature.ukpt") {
            return RenameDisposition.REVIEW to "rename only if the worked core feature is rebranded"
        }
        if (identifierAt(line, column) in workedCoreIdentifiers) {
            return RenameDisposition.REVIEW to "rename only if the worked core feature is rebranded"
        }
        if (path == "README.md") {
            return RenameDisposition.REVIEW to "project may keep or replace the worked template example"
        }
        if (source == "com.isaacudy.ukpt") {
            return RenameDisposition.REPLACE to "application package reference"
        }
        if (path.startsWith("feature/core/")) {
            return RenameDisposition.REVIEW to "project may keep or replace the worked template example"
        }
        if (path.startsWith("app/") || path == "gradle.properties") {
            return RenameDisposition.REPLACE to "project identity in an app-owned file"
        }
        return RenameDisposition.REVIEW to "outside the deterministic app allowlist"
    }

    /**
     * The environment-variable prefix is a runtime contract: the application reads the variables
     * and the convention plugins default them, so both sides have to be renamed together even
     * though `build-logic/` is otherwise protected. Its tests are not part of that contract —
     * they cover this planner, and renaming their fixtures would break them. The rest of the
     * protected tree is documentation a template update overwrites, where a rename would not
     * survive anyway.
     */
    private fun classifyEnvironmentPrefix(path: String): Pair<RenameDisposition, String> = when {
        path.startsWith("build-logic/src/main/") ->
            RenameDisposition.REPLACE to "environment-variable prefix, shared with the application"

        path == "UKPT.md" || protectedPrefixes.any(path::startsWith) ->
            RenameDisposition.KEEP to "template identity is protected"

        else -> RenameDisposition.REPLACE to "environment-variable prefix"
    }

    private fun replacementFor(
        source: String,
        line: String,
        column: Int,
        request: ProjectRenameRequest,
    ): String = when (source) {
        "com.isaacudy.ukpt" -> request.packageName
        "feature.ukpt" -> "<feature package or keep feature.ukpt>"
        // An environment-variable name has no place for the slug's hyphens.
        "UKPT" -> request.projectName.uppercase().replace('-', '_')
        "Ukpt" -> request.typePrefix
        "ukpt" -> if (identifierAt(line, column).length > source.length) {
            request.typePrefix.replaceFirstChar(Char::lowercaseChar)
        } else {
            request.projectName
        }
        else -> error("Unhandled identity token: $source")
    }

    private fun identifierAt(line: String, column: Int): String {
        var start = column
        while (start > 0 && Character.isJavaIdentifierPart(line[start - 1])) start--
        var end = column
        while (end < line.length && Character.isJavaIdentifierPart(line[end])) end++
        return line.substring(start, end)
    }
}
