package architecture

import architecture.rules.UkptArchitecture
import architecture.testing.ArchitectureDocsHarness

/** Doc↔catalog sync for UKPT's docs — see [ArchitectureDocsHarness]. */
class UkptArchitectureDocsTest : ArchitectureDocsHarness(UkptArchitecture)
