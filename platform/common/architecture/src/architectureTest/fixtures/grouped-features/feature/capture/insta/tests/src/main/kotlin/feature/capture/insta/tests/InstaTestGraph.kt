package feature.capture.insta.tests

import feature.capture.insta.captureInstaServerDependencies
import platform.testing.subcutaneous.SubcutaneousGraph
import platform.testing.subcutaneous.featureServer

val captureInstaTestGraph = SubcutaneousGraph(
    server = featureServer(captureInstaServerDependencies),
)
