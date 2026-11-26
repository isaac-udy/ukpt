package ukpt.coverage

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MutationResultsTest {

    private val root: File = Files.createTempDirectory("repository").toFile()
    private val main = "feature/a/server/src/main/kotlin/Greet.kt"
    private val other = "feature/a/server/src/main/kotlin/Other.kt"
    private val test = "feature/a/server/src/test/kotlin/GreetTest.kt"
    private val module = ModuleInfo(":feature:a:server", "feature/a/server", expectation = null)

    init {
        write(main, "class Greet")
        write(other, "class Other")
        write(test, "class GreetTest")
    }

    private fun write(path: String, text: String) = File(root, path).apply { parentFile.mkdirs(); writeText(text) }

    private fun hash(path: String) =
        MessageDigest.getInstance("SHA-256").digest(File(root, path).readBytes()).take(8).joinToString("") { "%02x".format(it) }

    private fun mutant(id: String, path: String, status: String = "survived") =
        """{"id": "$id", "path": "$path", "line": 1, "operator": "remove-call", "variant": "removed", "text": "x()", "status": "$status",
           "callee": "org.slf4j.Logger.info", "constructs": ["ServerDomain.UseCase"], "expected": ["unit"]}"""

    /** Results whose digest records the files as they are now. */
    private fun results(vararg mutants: String, withSources: Boolean = true): File {
        val files = listOf(main, other, test).joinToString { "\"$it\": \"${hash(it)}\"" }
        val sources = if (withSources) """, "sources": {"roots": ["feature/a"], "files": {$files}}""" else ""
        return File(root, "results.json").apply {
            writeText(
                """{"startedAt": "2026-11-26T01:52:00Z", "hosts": ["jvm"], "summary": {"millis": 1200, "notSampled": 4, "sampleSeed": 77},
                   "mutants": [${mutants.joinToString()}]$sources}""",
            )
        }
    }

    @Test
    fun resultsWithUnchangedSourcesKeepEveryMutant() {
        val results = MutationResults.read(results(mutant("a", main), mutant("b", other, "killed")), root)
        assertFalse(results.outdated)
        assertEquals(listOf("a", "b"), results.mutants.map { it.id })
        assertEquals("org.slf4j.Logger.info", results.mutants.first().callee)
        assertEquals(listOf("unit"), results.mutants.first().expected)
        assertEquals(77L, results.sampleSeed)
        assertEquals(4, results.notSampled)
        assertEquals("2026-11-26T01:52:00Z", results.startedAt.toString())
    }

    @Test
    fun aChangedProductionFileDropsOnlyThatFilesMutants() {
        val file = results(mutant("a", main), mutant("b", other))
        write(main, "class Greet2")
        val results = MutationResults.read(file, root)
        assertFalse(results.outdated)
        assertEquals(listOf("b"), results.mutants.map { it.id })
        assertEquals(1, results.droppedMutants)
        assertEquals(1, results.changedProductionFiles)
    }

    @Test
    fun anAddedOrChangedTestFileMakesEveryResultOutOfDate() {
        val file = results(mutant("a", main))
        write("feature/a/server/src/test/kotlin/NewTest.kt", "class NewTest")
        val added = MutationResults.read(file, root)
        assertTrue(added.outdated)
        assertTrue(added.mutants.isEmpty())
        assertTrue("NewTest.kt" in added.outdatedReason!!, added.outdatedReason)
    }

    @Test
    fun resultsWithoutASourceDigestAreOutOfDate() {
        val results = MutationResults.read(results(mutant("a", main), withSources = false), root)
        assertTrue(results.outdated)
        assertTrue(results.mutants.isEmpty())
    }

    @Test
    fun productionSourceSetsEndInMain() {
        assertTrue(MutationResults.isProductionSource("feature/a/client/src/commonMain/kotlin/A.kt"))
        assertTrue(MutationResults.isProductionSource("feature/a/server/src/main/kotlin/A.kt"))
        assertFalse(MutationResults.isProductionSource("feature/a/client/src/jvmTest/kotlin/A.kt"))
        assertFalse(MutationResults.isProductionSource("feature/a/tests/src/commonTest/kotlin/A.kt"))
    }

    @Test
    fun aMutantBelongsToTheNarrowestDeclarationThatContainsItsLine() {
        fun target(name: String?, startLine: Int, endLine: Int) =
            Target(main, module, name, startLine, endLine, emptyList(), Expectation.Unspecified, Basis.Unclassified, emptyMap())
        val outer = target("feature.a.Greet", 1, 8)
        val greet = target("feature.a.Greet.greet", 1, 1)
        val results = MutationResults.read(results(mutant("a", main, "killed")), root)
        val byTarget = results.byTarget(listOf(outer, greet))
        assertEquals(listOf("a"), byTarget.getValue(greet).map { it.id })
        assertEquals(MutantTally(killed = 1), MutantTally.of(byTarget.getValue(greet)))
    }
}
