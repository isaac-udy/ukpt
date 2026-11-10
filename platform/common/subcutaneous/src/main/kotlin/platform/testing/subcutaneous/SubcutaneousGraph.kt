package platform.testing.subcutaneous

import dev.isaacudy.udytils.postgres.PostgresConfig
import dev.isaacudy.udytils.postgres.koin.postgresDependencies
import org.koin.core.module.Module
import platform.server.postgres.postgresPlatformDependencies

/** The production Koin modules a feature's Subcutaneous Tests run, installed against each test's database. */
class SubcutaneousGraph(
    val server: (PostgresConfig) -> List<Module>,
)

/** [modules] with the Postgres modules every server graph needs. */
fun featureServer(vararg modules: Module): (PostgresConfig) -> List<Module> = { config ->
    listOf(postgresDependencies(config), postgresPlatformDependencies) + modules
}
