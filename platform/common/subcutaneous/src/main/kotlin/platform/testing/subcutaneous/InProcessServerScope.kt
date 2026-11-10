package platform.testing.subcutaneous

/** The body of an [inProcessServer] test: seed state, then load [baseUrl] in a browser. */
class InProcessServerScope internal constructor(
    private val scenario: Scenario,
    val baseUrl: String,
) {
    /** Runs a scenario against the running server; before a browser opens a page or while one is open. */
    suspend fun <T> given(block: suspend Scenario.() -> T): T = scenario.block()
}
