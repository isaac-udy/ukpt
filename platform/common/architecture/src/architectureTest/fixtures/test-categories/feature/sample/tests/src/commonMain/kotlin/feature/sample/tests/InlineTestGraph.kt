package feature.sample.tests

import org.koin.dsl.module
import platform.testing.subcutaneous.SubcutaneousGraph
import platform.testing.subcutaneous.featureServer

val inlineTestGraph = SubcutaneousGraph(
    client = listOf(module { }),
    server = featureServer(),
)
