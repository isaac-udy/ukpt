package feature.ukpt.tests

import feature.ukpt.ukptClientDependencies
import feature.ukpt.ukptServerDependencies
import platform.testing.subcutaneous.SubcutaneousGraph
import platform.testing.subcutaneous.featureClient
import platform.testing.subcutaneous.featureServer

val ukptTestGraph = SubcutaneousGraph(
    client = featureClient(ukptClientDependencies),
    server = featureServer(ukptServerDependencies),
)
