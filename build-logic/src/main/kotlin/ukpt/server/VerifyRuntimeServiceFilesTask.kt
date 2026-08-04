package ukpt.server

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Fails when two runtime dependencies declare the same `META-INF/services` path.
 *
 * The failure this prevents only exists in the packaged artifact, and only ever shows up in
 * production: on a normal classpath ServiceLoader reads every declaration, so `run`, the tests and
 * the migration suite all pass while the fat jar carries a truncated manifest. Checking the
 * classpath rather than the jar catches the whole class of it — including a pair of dependencies
 * nobody has collided yet — at the point where the offending dependency was added.
 */
abstract class VerifyRuntimeServiceFilesTask : DefaultTask() {

    /** The dependencies that will be folded into the deployable. */
    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    /**
     * The packaged module's own resource roots. A path declared here is a deliberate hand-merged
     * override, which wins in the fat jar, so a collision it covers is reported rather than failed.
     */
    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val moduleResources: ConfigurableFileCollection

    /** Where an override belongs, quoted in the report so the fix needs no further lookup. */
    @get:Input
    abstract val moduleResourceLocation: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val declarations = runtimeClasspath.files
            .map { ServiceFileDeclaration(origin = it.name, servicePaths = servicePathsIn(it)) }
            .filter { it.servicePaths.isNotEmpty() }
        val overrides = moduleResources.files.flatMap(::servicePathsInDirectory).toSet()

        val verdict = ServiceFileCollisions.analyse(declarations, overrides)
        val location = moduleResourceLocation.get()
        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText(verdict.render(location))
        }
        if (verdict.unhandled.isNotEmpty()) throw GradleException(verdict.failureMessage(location))
    }

    private fun servicePathsIn(entry: File): Set<String> = when {
        entry.isDirectory -> servicePathsInDirectory(entry)
        entry.isFile -> servicePathsInArchive(entry)
        else -> emptySet()
    }

    private fun servicePathsInDirectory(root: File): Set<String> {
        val services = File(root, ServiceFileCollisions.SERVICES_PREFIX)
        if (!services.isDirectory) return emptySet()
        return services.listFiles().orEmpty()
            .filter(File::isFile)
            .map { "${ServiceFileCollisions.SERVICES_PREFIX}${it.name}" }
            .toSet()
    }

    private fun servicePathsInArchive(archive: File): Set<String> = try {
        ZipFile(archive).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith(ServiceFileCollisions.SERVICES_PREFIX) }
                // Only the flat provider files are ServiceLoader's; anything nested below them is
                // some other tool's use of the directory and never collides with a provider list.
                .filter { it.name.count { character -> character == '/' } == 2 }
                .map { it.name }
                .toSet()
        }
    } catch (_: ZipException) {
        // A classpath entry that is not an archive at all carries no service declarations.
        emptySet()
    }
}
