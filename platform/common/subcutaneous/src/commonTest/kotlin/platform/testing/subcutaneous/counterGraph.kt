package platform.testing.subcutaneous

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

internal fun counterGraph(hostEdges: Edges.() -> Unit = {}) = SubcutaneousGraph(
    client = listOf(module { factoryOf(::FakeCounterViewModel) }),
    hostEdges = hostEdges,
)

internal fun counterGraphWithHostEdges() = counterGraph(hostEdges = { client { single { FakeNextCount(1) } } })
