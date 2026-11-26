package ukpt.coverage

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Package instrumentation and module expectations for the root `testCoverage { }` block. */
abstract class TestCoverageExtension {
    /** Module path → expectation, encoded as [Expectation.decode] reads it. */
    abstract val moduleExpectations: MapProperty<String, String>

    internal abstract val instrumentedPackages: SetProperty<String>

    fun instrumentedPackages(vararg patterns: String) {
        for (pattern in patterns) {
            require(pattern.length > 2 && pattern.endsWith(".*")) {
                "instrumentedPackages(\"$pattern\") requires a package pattern ending in .*, for example ukpt.tools.*."
            }
        }
        instrumentedPackages.addAll(*patterns)
    }

    /** Lines in [module] that no Construct claims are covered by a test in any one of [categories] (the ids in `architecture.definitions.TestCategories`). */
    fun testedBy(module: String, vararg categories: String) {
        require(categories.isNotEmpty()) { "testedBy(\"$module\") requires at least one test category id." }
        moduleExpectations.put(module, "anyOf:" + categories.joinToString(","))
    }

    /** No tests are expected to cover [module], for [reason]. */
    fun noTests(module: String, reason: String) {
        require(reason.isNotBlank()) { "noTests(\"$module\") requires a reason." }
        moduleExpectations.put(module, "none:$reason")
    }
}

/** Writes one Kover XML report per test category from the instrumented test runs. */
abstract class CollectTestCoverageTask : DefaultTask() {
    @get:InputFiles
    abstract val artifacts: ConfigurableFileCollection

    /** `module|task` → test category id. */
    @get:Input
    abstract val taskCategories: MapProperty<String, String>

    /** `module|task` → the task's JUnit XML results directory. */
    @get:Internal
    abstract val testResultDirectories: MapProperty<String, String>

    /** Module path → module directory, relative to [repositoryDirectory]. */
    @get:Input
    abstract val modules: MapProperty<String, String>

    @get:Input
    abstract val instrumentedPackages: SetProperty<String>

    @get:Input
    abstract val excludedClasses: SetProperty<String>

    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun collect() {
        val root = repositoryDirectory.get().asFile
        val problems = mutableListOf<String>()
        val artifacts = artifacts.files.filter { it.isFile && it.name != ".artifact" }.map { KoverArtifact.read(it, root) }
        val output = outputDirectory.get().asFile
        problems += uninstrumentedTasks(artifacts, testResultDirectories.get())
        val xmls = writeCategoryXmls(artifacts, taskCategories.get(), excludedClasses.get(), output.resolve("xml"), problems)
        val sourceDirs = artifacts.groupBy { it.variant }.mapValues { (_, list) ->
            list.flatMap { it.sourceDirs }.filter { it.isDirectory }.map { it.relativeTo(root).invariantSeparatorsPath }.distinct()
        }
        CoverageManifest(xmls, modules.get().toList(), sourceDirs, problems, instrumentedPackages.get(), excludedClasses.get())
            .write(output.resolve("manifest.json"), root)
    }
}

/** Joins the per-category coverage with the architecture classification and writes the report. */
abstract class TestCoverageReportTask : DefaultTask() {
    @get:InputFile
    abstract val manifest: RegularFileProperty

    @get:InputFile
    abstract val classification: RegularFileProperty

    @get:Input
    abstract val moduleExpectations: MapProperty<String, String>

    /** The `runMutants` results file; the report shows mutants when the file exists. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mutationResults: ConfigurableFileCollection

    /** Category id → display name, for categories that the classification does not name. */
    @get:Input
    abstract val categoryNames: MapProperty<String, String>

    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun render() {
        val root = repositoryDirectory.get().asFile
        val manifest = CoverageManifest.read(manifest.get().asFile, root)
        val classification = Classification.parse(classification.get().asFile.readText())
        manifest.checkInstrumentation(classification)
        val problems = manifest.problems.toMutableList()
        val names = categoryNames.get() + classification.categoryNames
        val expectations = moduleExpectations.get().mapValues { Expectation.decode(it.value) }
        expectations.forEach { (module, expectation) ->
            if (manifest.modules.none { it.first == module }) problems += "The testCoverage { } block declares `$module`, which is not a reported module."
            (expectation as? Expectation.AnyOf)?.categories?.filter { it !in names }?.forEach {
                problems += "The testCoverage { } block lists `$it` for `$module`, which is not a test category id."
            }
        }
        val modules = manifest.modules.map { (path, dir) -> ModuleInfo(path, dir, expectations[path]) }
        val sourceDirs = manifest.sourceDirsByVariant.mapValues { (_, dirs) -> dirs.map(root::resolve) }
        val generated = sortedSetOf<String>()
        val (hits, objectHeaders) = dropObjectHeaders(lineHits(manifest.xmls, sourceDirs, root, problems, generated), root)
        val targets = buildTargets(classification, modules, hits, problems)
        val notMeasured = notMeasured(modules, sourceDirs.values.flatten().toSet(), root)
        val mutations = mutationResults.files.firstOrNull { it.isFile }?.let { MutationResults.read(it, root) }
        val report = CoverageReport(targets, classification, modules, names, notMeasured, generated.toList(), objectHeaders, problems, mutations)

        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()
        HtmlReport(report, root, output).write()
        output.resolve("index.md").writeText(markdownReport(report))
        output.resolve("coverage.json").writeText(jsonReport(report))
        logger.lifecycle(consoleSummary(report) + "Report: ${output.resolve("index.html").toURI()}")
    }
}
