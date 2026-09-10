package architecture.rules.shared

import architecture.rules.UkptArchitecture
import com.lemonappdev.konsist.api.Konsist
import dev.isaacudy.udytils.architecture.ArchitectureRun
import dev.isaacudy.udytils.architecture.Violation
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The domain-interface audits over `src/architectureTest/fixtures/domain-audits`: a `feature.shop`
 * client whose `SubmitReviewImpl` injects four reads that only it consumes, whose
 * `AdministerTeamsImpl` injects a Create/Update/Delete family, whose `RequireReviewerImpl`
 * forwards one dependency, and whose `GetOrphan` nothing injects. `GetPromotions` and
 * `GetReviewAuthor` have several consumers and must stay out of every group.
 *
 * The app module beside it carries the three Koin resolution forms — `koin.get<T>()`,
 * `by inject<T>()`, `koinInject<T>()` — over `GetResolvedFromKoin`,
 * `GetResolvedByInjectDelegate`, and `GetResolvedInCompose`, plus a `bind GetBoundNotConsumed::class`
 * whose interface no one resolves.
 */
class DomainInterfaceAuditsTest {

    private val run = ArchitectureRun(
        UkptArchitecture.groups,
        scopeProvider = { Konsist.scopeFromExternalDirectory(File("src/architectureTest/fixtures/domain-audits").absolutePath) },
        membership = UkptArchitecture.membership,
    )

    private fun findings(ruleId: String): List<Violation> =
        run.auditFindings(run.rules.first { it.id == ruleId })

    @Test
    fun `reads with one consumer are reported as one projection candidate with the reads as evidence`() {
        val finding = findings("ClientDomain.DomainInterface.readProjections").single()
        assertEquals("SubmitReviewImpl", finding.where)
        assertEquals(
            listOf("FlowOfReviewRevision", "GetReviewDecisions", "GetReviewItems", "GetReviewTitle"),
            finding.evidence,
        )
        assertTrue("ReviewsRepository" in finding.message, finding.message)
    }

    @Test
    fun `reads with several consumers stay out of every sole-consumer group and family`() {
        val groupEvidence = (
            findings("ClientDomain.DomainInterface.readProjections") +
                findings("ClientDomain.DomainInterface.collapsedUpdateFamilies") +
                findings("ClientDomain.DomainInterface.namesACapability").filter { it.where == "AdministerTeamsImpl" }
            ).flatMap { it.evidence }.joinToString("\n")
        assertTrue("GetPromotions" !in groupEvidence, groupEvidence)
        assertTrue("GetReviewAuthor" !in groupEvidence, groupEvidence)
    }

    @Test
    fun `a consumer with high fan-in is reported once, grouped by provider`() {
        val findings = findings("ClientDomain.DomainInterface.namesACapability")
        val fanIn = findings.single { it.where == "SubmitReviewImpl" }
        assertTrue("injects 6 domain interfaces" in fanIn.message, fanIn.message)
        assertEquals(
            listOf("`ReviewsRepository`: FlowOfReviewRevision, GetPromotions, GetReviewAuthor, GetReviewDecisions, GetReviewItems, GetReviewTitle"),
            fanIn.evidence,
        )
    }

    @Test
    fun `a mixed group with one consumer lists reads and operations apart`() {
        val mixed = findings("ClientDomain.DomainInterface.namesACapability").single { it.where == "AdministerTeamsImpl" }
        assertEquals(listOf("operation `CreateTeam`", "operation `DeleteTeam`", "operation `UpdateTeam`"), mixed.evidence)
    }

    @Test
    fun `a read named after part of a domain model is reported, a plural of the model is not`() {
        val names = findings("ClientDomain.DomainInterface.namesACapability").map { it.where }
        assertTrue("GetReviewTitle" in names, names.toString())
        assertTrue("GetPromotions" !in names, names.toString())
        val title = findings("ClientDomain.DomainInterface.namesACapability").single { it.where == "GetReviewTitle" }
        assertTrue("`Title` of domain model `Review`" in title.message, title.message)
    }

    @Test
    fun `mutations on one noun from one provider are reported as an update-family candidate`() {
        val family = findings("ClientDomain.DomainInterface.collapsedUpdateFamilies").single()
        assertEquals("TeamsRepository Team", family.where)
        assertEquals(listOf("CreateTeam", "DeleteTeam", "UpdateTeam"), family.evidence)
    }

    @Test
    fun `an interface nothing injects and nothing resolves is reported`() {
        val unconsumed = findings("ClientDomain.DomainInterface.consumedInProduction").map { it.where }
        assertEquals(listOf("GetBoundNotConsumed (feature.shop)", "GetOrphan (feature.shop)"), unconsumed)
    }

    @Test
    fun `an interface resolved out of the Koin container is not reported`() {
        val unconsumed = findings("ClientDomain.DomainInterface.consumedInProduction").map { it.where }.toString()
        assertTrue("GetResolvedFromKoin" !in unconsumed, unconsumed)
        assertTrue("GetResolvedByInjectDelegate" !in unconsumed, unconsumed)
        assertTrue("GetResolvedInCompose" !in unconsumed, unconsumed)
    }

    @Test
    fun `a UseCase over one domain interface is reported`() {
        val single = findings("ClientDomain.UseCase.existsForADecision").single()
        assertTrue("RequireReviewerImpl" in single.message, single.message)
        assertTrue("GetReviewAuthor" in single.message, single.message)
    }

    @Test
    fun `the inventory reports one line per feature with counts`() {
        val inventory = findings("ClientDomain.inventory").single()
        assertEquals("feature.shop client.domain", inventory.where)
        assertEquals(
            "18 domain interfaces (0 published), 2 domain models, 4 UseCases; consumers per interface: none 2, one 14, several 2",
            inventory.message,
        )
    }
}
