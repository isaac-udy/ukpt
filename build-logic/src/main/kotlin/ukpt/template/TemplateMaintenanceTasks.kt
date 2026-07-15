package ukpt.template

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
        val issues = TemplateRepositoryValidator.validate(repositoryDirectory.get().asFile.toPath())
        if (issues.isNotEmpty()) {
            val report = issues.joinToString(separator = "\n") { "- ${it.path}: ${it.message}" }
            throw GradleException("UKPT template validation failed:\n$report")
        }
        logger.lifecycle("UKPT template validation passed")
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
