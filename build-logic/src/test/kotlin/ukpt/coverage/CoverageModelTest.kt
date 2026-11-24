package ukpt.coverage

import kotlin.test.Test
import kotlin.test.assertEquals

class CoverageModelTest {

    private val classification = Classification(
        categoryNames = mapOf("unit" to "Unit", "snapshot" to "Snapshot", "subcutaneous" to "Subcutaneous"),
        constructs = listOf(
            ConstructInfo("ClientUi.ViewModel", Expectation.AnyOf(listOf("subcutaneous", "unit"))),
            ConstructInfo("ClientUi.ScreenTags", Expectation.None("test tags")),
        ).associateBy { it.id },
        declarations = listOf(
            Declaration("feature/a/client/src/Vm.kt", 3, 10, "feature.a.Vm", listOf("ClientUi.ViewModel")),
            Declaration("feature/a/client/src/Vm.kt", 5, 6, "feature.a.Vm.Nested", emptyList(), group = "ClientUi"),
            Declaration("feature/a/client/src/Tags.kt", 1, 4, "feature.a.Tags", listOf("ClientUi.ScreenTags")),
        ),
    )
    private val feature = ModuleInfo(":feature:a:client", "feature/a/client", expectation = null)
    private val platform = ModuleInfo(":platform:x", "platform/x", Expectation.AnyOf(listOf("unit")))
    private val bare = ModuleInfo(":platform:y", "platform/y", expectation = null)

    private fun targets(hits: Map<String, Map<Int, Set<String>>>) =
        buildTargets(classification, listOf(feature, platform, bare), hits, mutableListOf())

    @Test
    fun aLineCountsAsCoveredOnlyWhenAnExpectedCategoryRunsIt() {
        val target = targets(mapOf("feature/a/client/src/Vm.kt" to mapOf(3 to setOf("unit"), 4 to setOf("snapshot"), 8 to emptySet()))).single()

        assertEquals(listOf(LineState.Covered, LineState.Incidental, LineState.Uncovered), listOf(3, 4, 8).map(target::state))
        assertEquals(Tally(covered = 1, incidental = 1, uncovered = 1), target.tally)
    }

    @Test
    fun aLineBelongsToTheInnermostDeclarationAndANestedDeclarationWithoutAConstructIsUnclassified() {
        val result = targets(mapOf("feature/a/client/src/Vm.kt" to mapOf(4 to setOf("unit"), 5 to setOf("unit"), 12 to emptySet())))

        assertEquals(
            listOf(
                Triple("feature.a.Vm", Basis.Construct, listOf(4)),
                Triple("feature.a.Vm.Nested", Basis.Unclassified, listOf(5)),
                Triple("Vm.kt", Basis.Unclassified, listOf(12)),
            ),
            result.map { Triple(it.name, it.basis, it.lines.keys.toList()) },
        )
    }

    @Test
    fun aModuleExpectationAppliesToLinesNoConstructClaims() {
        val result = targets(
            mapOf(
                "platform/x/src/A.kt" to mapOf(1 to setOf("unit")),
                "platform/y/src/B.kt" to mapOf(1 to setOf("unit")),
                "feature/a/client/src/Tags.kt" to mapOf(2 to setOf("unit")),
            ),
        ).associateBy { it.path }

        assertEquals(Basis.Module to LineState.Covered, result.getValue("platform/x/src/A.kt").let { it.basis to it.state(1) })
        assertEquals(Basis.NoExpectation to LineState.Run, result.getValue("platform/y/src/B.kt").let { it.basis to it.state(1) })
        assertEquals(Expectation.None("test tags"), result.getValue("feature/a/client/src/Tags.kt").expectation)
    }

    @Test
    fun aFileOutsideEveryModuleIsReportedAsAProblem() {
        val problems = mutableListOf<String>()
        buildTargets(classification, listOf(feature), mapOf("other/C.kt" to mapOf(1 to emptySet())), problems)

        assertEquals(1, problems.size)
    }

    @Test
    fun aTargetKeepsTheGroupTheClassifierGaveItsDeclaration() {
        val result = targets(mapOf("feature/a/client/src/Vm.kt" to mapOf(5 to setOf("unit"), 12 to emptySet())))

        assertEquals(listOf("ClientUi", null), result.map { it.group })
    }

    @Test
    fun theClassificationJsonGroupIsOptional() {
        val json = """{"categories": [], "constructs": [], "declarations": [
            {"path": "a/A.kt", "startLine": 1, "endLine": 2, "name": "a.A", "constructs": [], "group": "ClientDomain"},
            {"path": "a/B.kt", "startLine": 1, "endLine": 2, "name": "a.B", "constructs": [], "group": null},
            {"path": "a/C.kt", "startLine": 1, "endLine": 2, "name": "a.C", "constructs": []}
        ]}"""

        assertEquals(listOf("ClientDomain", null, null), Classification.parse(json).declarations.map { it.group })
    }

    @Test
    fun aModulePathSplitsIntoAreaFeatureAndAffinity() {
        fun parts(path: String) = ModuleInfo(path, "", expectation = null).let { listOf(it.area, it.featurePath, it.affinity) }

        assertEquals(listOf(":feature:core", ":feature:core", "api"), parts(":feature:core:api"))
        assertEquals(listOf(":feature:gate", ":feature:gate:auth", "client"), parts(":feature:gate:auth:client"))
        assertEquals(listOf(":app", ":app:client", "android"), parts(":app:client:android"))
        assertEquals(listOf(":platform", ":platform:client:design", "client"), parts(":platform:client:design"))
    }

    @Test
    fun formatsLineRanges() {
        assertEquals("3–5, 9, 11–12", lineRanges(listOf(3, 4, 5, 9, 11, 12)))
    }
}
