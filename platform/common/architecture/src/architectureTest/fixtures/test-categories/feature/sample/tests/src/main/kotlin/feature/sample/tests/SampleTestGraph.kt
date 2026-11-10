package feature.sample.tests

import feature.sample.sampleServerDependencies
import platform.testing.subcutaneous.SubcutaneousGraph
import platform.testing.subcutaneous.featureServer

val sampleTestGraph = SubcutaneousGraph(
    server = featureServer(sampleServerDependencies),
)
