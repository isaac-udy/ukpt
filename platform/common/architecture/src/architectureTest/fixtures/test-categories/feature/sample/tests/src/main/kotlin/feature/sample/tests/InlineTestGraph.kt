package feature.sample.tests

import feature.sample.server.data.SampleRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import platform.testing.subcutaneous.SubcutaneousGraph
import platform.testing.subcutaneous.featureServer

val inlineTestGraph = SubcutaneousGraph(
    server = featureServer(module { singleOf(::SampleRepository) }),
)
