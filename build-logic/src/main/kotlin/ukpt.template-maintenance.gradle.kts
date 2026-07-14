import ukpt.template.PlanProjectRenameTask
import ukpt.template.ValidateTemplateTask

tasks.register<ValidateTemplateTask>("validateTemplate") {
    group = "verification"
    description = "Validates UKPT's marker, migrations, agent guidance, and shared skills."
    repositoryDirectory.set(layout.projectDirectory)
}

tasks.register<PlanProjectRenameTask>("planProjectRename") {
    group = "ukpt"
    description = "Writes a safe, classified plan for renaming a fresh UKPT project."
    repositoryDirectory.set(layout.projectDirectory)
    projectName.convention(providers.gradleProperty("ukpt.newProjectName"))
    packageName.convention(providers.gradleProperty("ukpt.newProjectPackage"))
    typePrefix.convention(providers.gradleProperty("ukpt.newProjectTypePrefix"))
    failOnReplace.convention(
        providers.gradleProperty("ukpt.renameFailOnReplace").map(String::toBoolean).orElse(false),
    )
    reportFile.set(layout.buildDirectory.file("reports/ukpt/project-rename-plan.txt"))
}
