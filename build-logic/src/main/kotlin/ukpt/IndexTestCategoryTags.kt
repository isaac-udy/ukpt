package ukpt

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.io.InputStream
import java.net.URLClassLoader

@CacheableTask
abstract class IndexTestCategoryTags : DefaultTask() {
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testClasses: ConfigurableFileCollection

    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:Input
    abstract val jupiterOnly: Property<Boolean>

    @get:OutputFile
    abstract val indexFile: RegularFileProperty

    @TaskAction
    fun index() {
        val urls = runtimeClasspath.files.map { it.toURI().toURL() }.toTypedArray()
        val tags = URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { loader ->
            if (jupiterOnly.get() || hasOnlyJupiter(loader)) TestTags(loader).read(testClasses.files) else setOf("*")
        }
        indexFile.get().asFile.writeText(tags.sorted().joinToString("\n"))
    }

    private fun hasOnlyJupiter(loader: ClassLoader): Boolean =
        loader.getResources("META-INF/services/org.junit.platform.engine.TestEngine").asSequence()
            .flatMap { url -> url.openStream().bufferedReader().use { it.readLines() } }
            .map { it.substringBefore('#').trim() }
            .filter(String::isNotEmpty)
            .toSet() == setOf("org.junit.jupiter.engine.JupiterTestEngine")
}

// A possible tag keeps the whole host's classes available for JUnit discovery, including enclosing classes.
internal class TestTags(private val loader: ClassLoader) {
    private val tags = mutableSetOf<String>()
    private val visited = mutableSetOf<String>()

    fun read(classes: Set<File>): Set<String> {
        classes.forEach { file -> file.inputStream().use(::scan) }
        return tags
    }

    private fun scan(input: InputStream) {
        val node = ClassNode()
        ClassReader(input).accept(node, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        node.visibleAnnotations.orEmpty().forEach(::annotation)
        node.methods.forEach { it.visibleAnnotations.orEmpty().forEach(::annotation) }
        node.superName?.let(::follow)
        node.interfaces.forEach(::follow)
    }

    private fun annotation(annotation: AnnotationNode) {
        when (annotation.desc) {
            "Lorg/junit/jupiter/api/Tag;" -> tags += annotation.values[1] as String
            "Lorg/junit/jupiter/api/Tags;" -> (annotation.values[1] as List<*>)
                .filterIsInstance<AnnotationNode>().forEach(::annotation)
            else -> follow(Type.getType(annotation.desc).internalName, optionalAnnotation = true)
        }
    }

    private fun follow(name: String, optionalAnnotation: Boolean = false) {
        if (name.startsWith("java/") || name.startsWith("kotlin/") || !visited.add(name)) return
        val resource = loader.getResourceAsStream("$name.class")
        if (resource != null) {
            resource.use(::scan)
        } else if (!optionalAnnotation) {
            tags += "*"
        }
    }
}
