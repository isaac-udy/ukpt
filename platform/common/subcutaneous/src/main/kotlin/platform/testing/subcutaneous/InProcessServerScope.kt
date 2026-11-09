package platform.testing.subcutaneous

import org.koin.core.module.Module

/** The body of an [inProcessServer] test: seed state, then start the application against the server. */
class InProcessServerScope internal constructor(
    private val scenario: Scenario,
    /** Load these after the application's client modules: the transport to the in-process server, then the client edges. */
    val clientEdges: List<Module>,
) {
    /** Runs a scenario against the running server; before the application starts or while it runs. */
    suspend fun <T> given(block: suspend Scenario.() -> T): T = scenario.block()
}
