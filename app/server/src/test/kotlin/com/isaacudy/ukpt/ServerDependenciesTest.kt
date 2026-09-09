package com.isaacudy.ukpt

import dev.isaacudy.udytils.postgres.PostgresConfig
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Every constructor parameter of every registered class must have a definition in the graph.
 * Nothing is instantiated, so no database is needed. Verified as one module including the whole
 * list: `verifyAll` checks each module alone, so a dependency bound by another module reads as
 * missing. A parameter with a default value only warns here while `singleOf` still resolves it at
 * runtime; `ProjectRules.injectableConstructorsHaveNoDefaults` covers that. An entry point the
 * server resolves lazily (a job, a worker) also needs a test that resolves it from a running graph.
 */
@OptIn(KoinExperimentalAPI::class)
class ServerDependenciesTest {

    @Test
    fun `every registered constructor parameter has a definition`() {
        val postgresConfig = PostgresConfig(jdbcUrl = "jdbc:postgresql://localhost:5432/verify", username = "verify", password = "verify")
        module { includes(serverDependencies(postgresConfig)) }.verify()
    }
}
