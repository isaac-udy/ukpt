package platform.testing.subcutaneous

import dev.isaacudy.udytils.postgres.PostgresConfig
import dev.isaacudy.udytils.postgres.koin.postgresDependencies
import org.koin.core.module.Module
import platform.network.networkDependencies
import platform.server.postgres.postgresPlatformDependencies

/**
 * The production Koin modules a feature's Subcutaneous Tests run: [client] builds the ViewModels,
 * [server] is installed in the in-process server against each test's database.
 */
class SubcutaneousGraph(
    val client: List<Module>,
    val server: (PostgresConfig) -> List<Module>,
)

/** [modules] with the network modules every client graph needs. */
fun featureClient(vararg modules: Module): List<Module> = listOf(networkDependencies) + modules

/** [modules] with the Postgres modules every server graph needs. */
fun featureServer(vararg modules: Module): (PostgresConfig) -> List<Module> = { config ->
    listOf(postgresDependencies(config), postgresPlatformDependencies) + modules
}
