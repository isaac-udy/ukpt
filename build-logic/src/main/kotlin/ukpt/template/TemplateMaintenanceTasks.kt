package ukpt.template

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

/**
 * Validates that the current checkout still satisfies UKPT's template-maintenance invariants.
 *
 * The task checks template metadata and migrations, shared agent guidance, canonical skill
 * metadata, and Claude compatibility links. It reports all discovered issues in one failure and is
 * deliberately untracked so every invocation inspects the live checkout.
 */
@UntrackedTask(because = "Validation should inspect the current repository on every run")
abstract class ValidateTemplateTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @TaskAction
    fun validateTemplate() {
        val repository = repositoryDirectory.get().asFile
        val gitRoot = git(repository, "rev-parse", "--show-toplevel")?.trim()?.let { File(it).toPath().toRealPath() }
        val issues = TemplateRepositoryValidator.validate(
            repository = repository.toPath().toRealPath(),
            trackedFiles = trackedFiles(repository),
            gitRoot = gitRoot ?: repository.toPath().toRealPath(),
        )
        if (issues.isNotEmpty()) {
            val report = issues.joinToString(separator = "\n") { "- ${it.path}: ${it.message}" }
            throw GradleException("UKPT template validation failed:\n$report")
        }
        logger.lifecycle("UKPT template validation passed")
    }

    /**
     * Null outside a git checkout, which skips the checks that need the tracked-file list. Run from
     * a nested project, `git ls-files` lists only that project's files, relative to it.
     */
    private fun trackedFiles(repository: File): List<String>? =
        git(repository, "ls-files", "-z")?.split('\u0000')?.filter(String::isNotEmpty)

    private fun git(directory: File, vararg args: String): String? {
        val process = runCatching {
            ProcessBuilder("git", *args).directory(directory).redirectErrorStream(false).start()
        }.getOrNull() ?: return null
        val output = process.inputStream.bufferedReader().readText()
        process.errorStream.readBytes()
        if (process.waitFor() != 0) return null
        return output
    }
}

/**
 * Writes a classified, non-mutating inventory for renaming a fresh UKPT project checkout.
 *
 * The report separates required replacements from project-specific review items and protected
 * template identifiers. When [failOnReplace] is enabled, the task fails after writing the report if
 * any required replacements remain, which makes it suitable for post-rename verification.
 */
@UntrackedTask(because = "The plan should inspect the current repository on every run")
abstract class PlanProjectRenameTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val typePrefix: Property<String>

    @get:Input
    abstract val failOnReplace: Property<Boolean>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun planRename() {
        val request = ProjectRenameRequest(
            projectName = projectName.get(),
            packageName = packageName.get(),
            typePrefix = typePrefix.get(),
        )
        val errors = ProjectRenamePlanner.validate(request)
        if (errors.isNotEmpty()) {
            throw GradleException(errors.joinToString(prefix = "Invalid rename request:\n- ", separator = "\n- "))
        }

        val plan = ProjectRenamePlanner.plan(repositoryDirectory.get().asFile.toPath(), request)
        val report = reportFile.get().asFile
        report.parentFile.mkdirs()
        report.writeText(plan.render())

        val counts = RenameDisposition.entries.joinToString { disposition ->
            "$disposition=${plan.occurrences.count { it.disposition == disposition }}"
        }
        logger.lifecycle("Project rename plan written to ${report.absolutePath} ($counts)")

        val remainingReplacements = plan.occurrences.count { it.disposition == RenameDisposition.REPLACE }
        if (failOnReplace.get() && remainingReplacements > 0) {
            throw GradleException(
                "$remainingReplacements required project-identity replacement(s) remain; " +
                    "review ${report.absolutePath}",
            )
        }
    }
}
