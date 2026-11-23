package feature.sample.tests

import feature.sample.sampleClientDependencies
import platform.testing.subcutaneous.SubcutaneousGraph
import platform.testing.subcutaneous.featureClient
import platform.testing.subcutaneous.featureServer

val sampleTestGraph = SubcutaneousGraph(
    client = featureClient(sampleClientDependencies),
    server = featureServer(),
)
