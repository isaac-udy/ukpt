package architecture.rules.shared

import architecture.rules.UkptArchitecture
import com.lemonappdev.konsist.api.Konsist
import dev.isaacudy.udytils.architecture.ArchitectureRun
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `declaredInItsInterfaceFile` over `src/architectureTest/fixtures/usecase-files`: `ReorderImpl`
 * has its own file beside `Reorder` in the client module; `RefundImpl` shares `Refund.kt`;
 * `CheckoutImpl` implements an interface published to `:api`.
 */
class UseCaseFileTest {

    private val run = ArchitectureRun(
        UkptArchitecture.groups,
        scopeProvider = { Konsist.scopeFromExternalDirectory(File("src/architectureTest/fixtures/usecase-files").absolutePath) },
        membership = UkptArchitecture.membership,
    )

    @Test
    fun `only the UseCase in its own file beside its interface is reported`() {
        val rule = run.rules.first { it.id == "ClientDomain.UseCase.declaredInItsInterfaceFile" }
        val violation = run.violations(rule).single()
        assertTrue(violation.where.endsWith("/domain/ReorderImpl.kt:3:1"), violation.where)
        assertEquals("UseCase `ReorderImpl` has its own file beside `Reorder` in the same package; declare it in `Reorder.kt`", violation.message)
    }
}
