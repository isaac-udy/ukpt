package feature.capture.insta.tests

import feature.capture.insta.captureInstaClientDependencies
import platform.testing.subcutaneous.SubcutaneousGraph

val captureInstaTestGraph = SubcutaneousGraph(
    client = listOf(captureInstaClientDependencies),
)
