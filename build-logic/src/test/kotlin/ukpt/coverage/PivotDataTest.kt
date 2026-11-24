package ukpt.coverage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PivotDataTest {

    private val module = ModuleInfo(":feature:a:client", "feature/a/client", expectation = null)
    private val path = "feature/a/client/src/commonMain/kotlin/feature/a/client/ui/Vm.kt"

    private fun target(name: String?, constructs: List<String>, expectation: Expectation, basis: Basis, startLine: Int, group: String? = null) =
        Target(path, module, name, startLine, startLine + 2, constructs, expectation, basis, mapOf(startLine to setOf("unit"), startLine + 1 to emptySet()), group)

    private val report = CoverageReport(
        targets = listOf(
            target("feature.a.client.ui.Vm.Nested", listOf("ClientUi.ViewModel"), Expectation.AnyOf(listOf("unit")), Basis.Construct, 3),
            target("feature.a.client.ui.Helper", emptyList(), Expectation.Unspecified, Basis.Unclassified, 12, group = "ClientUi"),
            target(null, emptyList(), Expectation.Unspecified, Basis.Unclassified, 20),
        ),
        classification = Classification(emptyMap(), emptyMap(), emptyList()),
        modules = listOf(module),
        categoryNames = mapOf("unit" to "Unit", "snapshot" to "</script>"),
        notMeasured = emptyList(),
        generatedFiles = emptyList(),
        objectHeaderLines = 0,
        problems = emptyList(),
    )

    private val json = pivotData(report, packageOf = { "feature.a.client.ui" }, linkOf = { "files/vm.html#d${it.startLine}" })

    private fun rows() = Json.parseToJsonElement(json.replace("<\\/", "</")).jsonObject.getValue("rows").jsonArray.map { row ->
        PIVOT_FIELDS.zip(row.jsonArray).toMap()
    }

    @Test
    fun aDeclarationIsNamedRelativeToTheFilePackage() {
        val row = rows().first()
        assertEquals("Vm.Nested", row.getValue("short").jsonPrimitive.content)
        assertEquals("feature.a.client.ui", row.getValue("package").jsonPrimitive.content)
        assertEquals("ClientUi.ViewModel", row.getValue("construct").jsonPrimitive.content)
        assertEquals("""["unit"]""", row.getValue("expected").toString())
        assertEquals(listOf(1, 1), listOf("covered", "uncovered").map { row.getValue(it).jsonPrimitive.content.toInt() })
        assertEquals("files/vm.html#d3", row.getValue("link").jsonPrimitive.content)
        assertEquals(listOf(":feature:a", ":feature:a", "client", "ClientUi"), listOf("area", "feature", "affinity", "layer").map { row.getValue(it).jsonPrimitive.content })
    }

    @Test
    fun aDeclarationWithoutAConstructTakesItsLayerFromTheClassifierGroup() {
        assertEquals(listOf("ClientUi", "Unclassified"), rows().drop(1).map { it.getValue("layer").jsonPrimitive.content })
    }

    @Test
    fun linesOutsideEveryDeclarationAreNamedForTheFile() {
        val row = rows().last()
        assertEquals("Vm.kt (outside declarations)", row.getValue("short").jsonPrimitive.content)
        assertEquals("unspecified", row.getValue("expected").jsonPrimitive.content)
        assertEquals("unclassified", row.getValue("basis").jsonPrimitive.content)
    }

    @Test
    fun theDataCannotCloseItsScriptElement() {
        assertFalse("</" in json)
    }

    @Test
    fun theDeclaredPackageIsReadFromTheSource() {
        assertEquals("feature.a.client.ui", declaredPackage("// header\n@file:JvmName(\"X\")\npackage feature.a.client.ui\n\nclass Vm"))
        assertEquals("", declaredPackage("class Vm"))
    }
}
