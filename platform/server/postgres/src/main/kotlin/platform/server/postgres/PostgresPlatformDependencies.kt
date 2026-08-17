package platform.server.postgres

import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The Postgres primitives this project owns. An application installs this beside
 * `dev.isaacudy.udytils.postgres.koin.postgresDependencies(config)`, which is where the [Database]
 * resolved below comes from.
 */
val postgresPlatformDependencies: Module = module {
    single<TransactionRunner> { TransactionRunner(get<Database>()) }
}
