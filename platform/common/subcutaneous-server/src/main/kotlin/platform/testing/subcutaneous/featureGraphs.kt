package platform.testing.subcutaneous

import dev.isaacudy.udytils.postgres.koin.postgresDependencies
import org.koin.core.module.Module
import platform.server.postgres.postgresPlatformDependencies

/** [modules] with the Postgres modules, run in-process against each test's database. */
fun featureServer(vararg modules: Module): SubcutaneousServer = InProcessServer { config ->
    listOf(postgresDependencies(config), postgresPlatformDependencies) + modules
}
