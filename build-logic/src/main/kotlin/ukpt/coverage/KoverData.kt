package ukpt.coverage

import kotlinx.kover.features.jvm.ClassFilters
import kotlinx.kover.features.jvm.KoverLegacyFeatures
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

/**
 * One `build/kover/<variant>.artifact` file: blank-line-separated sections that list the module's
 * source directories (after the module path on the first line), class directories and `.ic` files.
 */
data class KoverArtifact(val module: String, val variant: String, val sourceDirs: List<File>, val classDirs: List<File>, val ics: List<File>) {
    companion object {
        fun read(file: File, root: File): KoverArtifact {
            val sections = file.readText().split("\n\n").map { section -> section.lines().filter { it.isNotBlank() } }
            fun files(index: Int, skip: Int = 0) = sections.getOrNull(index).orEmpty().drop(skip).map { root.resolve(it) }
            return KoverArtifact(
                module = sections.first().first(),
                variant = file.nameWithoutExtension,
                sourceDirs = files(0, skip = 1),
                classDirs = files(1),
                ics = files(2),
            )
        }
    }
}

data class CategoryXml(val category: String, val variant: String, val file: File)

/** Kover writes one `.ic` file per instrumented test task, named after the task. */
fun icTaskName(ic: File): String = ic.nameWithoutExtension

/**
 * Writes one Kover XML report per category and variant. Each report combines the category's `.ic`
 * files from every module against every module's classes, so a test in one module covers classes
 * in another.
 */
fun writeCategoryXmls(
    artifacts: List<KoverArtifact>,
    taskCategories: Map<String, String>,
    excludedClasses: Set<String>,
    outputDir: File,
    problems: MutableList<String>,
): List<CategoryXml> {
    outputDir.deleteRecursively()
    outputDir.mkdirs()
    val filters = ClassFilters(emptySet(), excludedClasses, emptySet(), emptySet(), emptySet(), emptySet())
    val xmls = mutableListOf<CategoryXml>()
    for ((variant, variantArtifacts) in artifacts.groupBy { it.variant }.toSortedMap()) {
        val icsByCategory = mutableMapOf<String, MutableList<File>>()
        for (artifact in variantArtifacts) {
            for (ic in artifact.ics.filter { it.exists() }) {
                val category = taskCategories["${artifact.module}|${icTaskName(ic)}"]
                if (category == null) {
                    problems += "`${artifact.module}:${icTaskName(ic)}` wrote coverage that maps to no test category; the coverage is left out."
                } else {
                    icsByCategory.getOrPut(category) { mutableListOf() } += ic
                }
            }
        }
        val classDirs = variantArtifacts.flatMap { it.classDirs }.filter { it.exists() }
        val sourceDirs = variantArtifacts.flatMap { it.sourceDirs }.filter { it.exists() }
        for ((category, ics) in icsByCategory.toSortedMap()) {
            val file = outputDir.resolve("$category.$variant.xml")
            KoverLegacyFeatures.generateXmlReport(file, ics, classDirs, sourceDirs, "$category ($variant)", filters)
            xmls += CategoryXml(category, variant, file)
        }
    }
    return xmls
}

/** `package/path/File.kt` → line → whether a test ran the line. */
fun parseKoverXml(file: File): Map<String, Map<Int, Boolean>> {
    val factory = XMLInputFactory.newInstance().apply {
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    }
    val result = mutableMapOf<String, MutableMap<Int, Boolean>>()
    file.inputStream().use { input ->
        val reader = factory.createXMLStreamReader(input)
        var packageName = ""
        var current: MutableMap<Int, Boolean>? = null
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                    "package" -> packageName = reader.getAttributeValue(null, "name")
                    "sourcefile" -> current = result.getOrPut("$packageName/${reader.getAttributeValue(null, "name")}".removePrefix("/")) { mutableMapOf() }
                    "line" -> current?.let { lines ->
                        val number = reader.getAttributeValue(null, "nr").toInt()
                        val covered = reader.getAttributeValue(null, "ci").toInt() > 0
                        lines[number] = (lines[number] ?: false) || covered
                    }
                }
                XMLStreamConstants.END_ELEMENT -> if (reader.localName == "sourcefile") current = null
            }
        }
    }
    return result
}

private val PACKAGE = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)

/**
 * Maps the `package/path/File.kt` keys Kover's XML uses to repository paths, by reading each
 * source file's `package` declaration: a multiplatform source file's directory need not match its package.
 */
fun sourceIndex(sourceDirs: List<File>, root: File, problems: MutableList<String>): Map<String, String> {
    val index = mutableMapOf<String, String>()
    for (dir in sourceDirs.distinct().filter { it.isDirectory }) {
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val packageName = PACKAGE.find(file.readText())?.groupValues?.get(1).orEmpty()
            val key = (packageName.replace('.', '/') + "/" + file.name).removePrefix("/")
            val path = file.relativeTo(root).invariantSeparatorsPath
            val existing = index.putIfAbsent(key, path)
            if (existing != null && existing != path) {
                problems += "`$existing` and `$path` declare the same package and file name; Kover's report cannot tell them apart, so `$path` is left out."
            }
        }
    }
    return index
}

/** Repository path → line → the categories whose tests ran the line. */
fun lineHits(
    xmls: List<CategoryXml>,
    sourceDirsByVariant: Map<String, List<File>>,
    root: File,
    problems: MutableList<String>,
    generated: MutableSet<String>,
): Map<String, Map<Int, Set<String>>> {
    val indexes = sourceDirsByVariant.mapValues { (_, dirs) -> sourceIndex(dirs, root, problems) }
    val hits = mutableMapOf<String, MutableMap<Int, MutableSet<String>>>()
    val unmapped = mutableSetOf<String>()
    for (xml in xmls) {
        val index = indexes[xml.variant].orEmpty()
        for ((key, lines) in parseKoverXml(xml.file)) {
            val path = index[key]
            if (path == null) {
                unmapped += key
                continue
            }
            // Generated sources live under a module's build directory; no Construct classifies them.
            if ("build" in path.split('/')) {
                generated += path
                continue
            }
            val fileHits = hits.getOrPut(path) { mutableMapOf() }
            for ((line, covered) in lines) {
                val categories = fileHits.getOrPut(line) { mutableSetOf() }
                if (covered) categories += xml.category
            }
        }
    }
    unmapped.sorted().forEach { problems += "Kover reported `$it`, which matches no source file; its lines are left out." }
    return hits
}

private val PRODUCTION_SOURCE_SET = Regex("""^(main|\w*Main)$""")

/** Counts the production Kotlin files in each module source set that no measured variant compiles. */
fun notMeasured(modules: List<ModuleInfo>, measuredDirs: Set<File>, root: File): List<NotMeasured> {
    val result = mutableListOf<NotMeasured>()
    for (module in modules) {
        val src = root.resolve(module.directory).resolve("src")
        val sourceSets = src.listFiles().orEmpty().filter { it.isDirectory && PRODUCTION_SOURCE_SET.matches(it.name) }
        for (sourceSet in sourceSets.sortedBy { it.name }) {
            val files = sourceSet.walkTopDown().filter { file ->
                file.isFile && file.extension == "kt" && measuredDirs.none { file.startsWith(it) }
            }.count()
            if (files > 0) result += NotMeasured(module.path, sourceSet.name, files)
        }
    }
    return result
}

/** What `collectTestCoverage` hands `testCoverageReport`: the XML reports and each module's source directories. */
class CoverageManifest(
    val xmls: List<CategoryXml>,
    val modules: List<Pair<String, String>>,
    val sourceDirsByVariant: Map<String, List<String>>,
    val problems: List<String>,
) {
    fun write(file: File, root: File) {
        fun rel(f: File) = JsonPrimitive(f.relativeTo(root).invariantSeparatorsPath)
        val json = JsonObject(
            mapOf(
                "xmls" to JsonArray(xmls.map { JsonObject(mapOf("category" to JsonPrimitive(it.category), "variant" to JsonPrimitive(it.variant), "file" to rel(it.file))) }),
                "modules" to JsonArray(modules.map { (path, dir) -> JsonObject(mapOf("path" to JsonPrimitive(path), "directory" to JsonPrimitive(dir))) }),
                "sourceDirs" to JsonObject(sourceDirsByVariant.mapValues { (_, dirs) -> JsonArray(dirs.map(::JsonPrimitive)) }),
                "problems" to JsonArray(problems.map(::JsonPrimitive)),
            ),
        )
        file.parentFile.mkdirs()
        file.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), json))
    }

    companion object {
        fun read(file: File, root: File): CoverageManifest {
            val json = Json.parseToJsonElement(file.readText()).jsonObject
            fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
            return CoverageManifest(
                xmls = json.getValue("xmls").jsonArray.map { it.jsonObject }.map { CategoryXml(it.string("category"), it.string("variant"), root.resolve(it.string("file"))) },
                modules = json.getValue("modules").jsonArray.map { it.jsonObject }.map { it.string("path") to it.string("directory") },
                sourceDirsByVariant = json.getValue("sourceDirs").jsonObject.mapValues { (_, dirs) -> dirs.jsonArray.map { it.jsonPrimitive.content } },
                problems = json.getValue("problems").jsonArray.map { it.jsonPrimitive.content },
            )
        }
    }
}

private val OBJECT_HEADER = Regex("""^\s*(?:(?:private|internal|public|protected|data|companion)\s+)*object\b[^=]*\{\s*$""")

/**
 * Removes each line that opens an `object` body. Kover attributes the object's instance creation to
 * that line, and the instance is created only when code reads the object itself, not when code uses
 * a declaration nested in it. Returns the remaining hits and the number of lines removed.
 */
fun dropObjectHeaders(hits: Map<String, Map<Int, Set<String>>>, root: File): Pair<Map<String, Map<Int, Set<String>>>, Int> {
    var dropped = 0
    val kept = hits.mapValues { (path, lines) ->
        val source = root.resolve(path).takeIf { it.isFile }?.readLines().orEmpty()
        lines.filterKeys { line ->
            val header = source.getOrNull(line - 1)?.let(OBJECT_HEADER::matches) == true
            if (header) dropped++
            !header
        }
    }
    return kept to dropped
}
