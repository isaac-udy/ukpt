package com.isaacudy.ukpt

import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify
import platform.testing.WiringTest
import kotlin.test.Test

/**
 * Every constructor parameter of every registered class, ViewModels included, must have a
 * definition in the graph. The test instantiates no class. Verified as one module including the whole
 * list: `verifyAll` checks each module alone, so a dependency bound by another module reads as
 * missing. A parameter with a default value only warns here while `singleOf` still resolves it at
 * runtime; `ProjectRules.injectableConstructorsHaveNoDefaults` covers that.
 */
@WiringTest
@OptIn(KoinExperimentalAPI::class)
class ClientDependenciesTest {

    @Test
    fun `every registered constructor parameter has a definition`() {
        module { includes(clientDependencies) }.verify()
    }
}
