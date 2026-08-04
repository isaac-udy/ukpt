package platform.server.postgres

import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The Postgres primitives this project owns, wired beside
 * `dev.isaacudy.udytils.postgres.koin.postgresDependencies(config)`: that module
 * provides the `DataSource`, the Exposed [Database], the migrator and the
 * notification bus, and this one provides the [TransactionRunner] over the
 * [Database] it resolves from there. An application wires both.
 */
val postgresPlatformDependencies: Module = module {
    single<TransactionRunner> { TransactionRunner(get<Database>()) }
}
