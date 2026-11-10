package feature.ukpt.tests

import feature.ukpt.ukptServerDependencies
import platform.testing.subcutaneous.SubcutaneousGraph
import platform.testing.subcutaneous.featureServer

val ukptTestGraph = SubcutaneousGraph(
    server = featureServer(ukptServerDependencies),
)
