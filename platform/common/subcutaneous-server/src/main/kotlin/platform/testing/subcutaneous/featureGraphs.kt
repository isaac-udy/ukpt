package platform.testing.subcutaneous

import dev.isaacudy.udytils.postgres.koin.postgresDependencies
import org.koin.core.module.Module
import platform.network.networkDependencies
import platform.server.postgres.postgresPlatformDependencies

/** [modules] with the network modules every client graph needs. */
fun featureClient(vararg modules: Module): List<Module> = listOf(networkDependencies) + modules

/** [modules] with the Postgres modules, run in-process against each test's database. */
fun featureServer(vararg modules: Module): SubcutaneousServer = InProcessServer { config ->
    listOf(postgresDependencies(config), postgresPlatformDependencies) + modules
}
