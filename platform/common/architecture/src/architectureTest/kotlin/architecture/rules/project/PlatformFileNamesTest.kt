package architecture.rules.project

import architecture.rules.UkptArchitecture
import com.lemonappdev.konsist.api.Konsist
import dev.isaacudy.udytils.architecture.ArchitectureRun
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `ProjectRules.platformFileNamesDistinctFromCommon` over `src/architectureTest/fixtures/platform-file-names`:
 * a `feature.store` client whose `commonMain` declares `DriverFactory.kt`, whose `androidMain`
 * repeats that file name, and whose `iosMain` and `jvmMain` use the `.ios.kt` and `.jvm.kt` suffixes.
 */
class PlatformFileNamesTest {

    private val fixtures = File("src/architectureTest/fixtures/platform-file-names").absolutePath

    private val run = ArchitectureRun(
        UkptArchitecture.groups,
        scopeProvider = { Konsist.scopeFromExternalDirectory(fixtures) },
        membership = UkptArchitecture.membership,
    )

    @Test
    fun `only the platform file that repeats the commonMain file name is reported`() {
        val violations = run.violations(run.rules.first { it.id == "ProjectRules.platformFileNamesDistinctFromCommon" })

        assertEquals(listOf("androidMain/kotlin/feature/store/client/data/DriverFactory.kt"), violations.map { it.where.substringAfterLast("/src/") }, violations.toString())
        assertEquals(true, "rename it `DriverFactory.android.kt`" in violations.single().message)
    }
}
