package architecture

import architecture.rules.UkptArchitecture
import dev.isaacudy.udytils.architecture.testing.ArchitectureDocsHarness

/** Doc↔catalog sync for UKPT's docs — see [ArchitectureDocsHarness]. */
class UkptArchitectureDocsTest : ArchitectureDocsHarness(UkptArchitecture)
