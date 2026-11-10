package platform.testing.subcutaneous

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bindings that replace production ones at the edges of the system: a backend the project does
 * not own, a clock. Loaded after the graph, so a binding here overrides the production one.
 */
class Edges internal constructor() {
    private val serverBlocks = mutableListOf<Module.() -> Unit>()

    fun server(block: Module.() -> Unit) {
        serverBlocks += block
    }

    internal fun serverModule(): Module = module { serverBlocks.forEach { it() } }
}
