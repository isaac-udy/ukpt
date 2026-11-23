package platform.testing.subcutaneous

import org.koin.core.module.Module
import platform.network.networkDependencies

/** [modules] with the network modules every client graph needs. */
fun featureClient(vararg modules: Module): List<Module> = listOf(networkDependencies) + modules
