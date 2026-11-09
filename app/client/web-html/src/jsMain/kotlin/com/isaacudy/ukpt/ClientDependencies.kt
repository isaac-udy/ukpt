package com.isaacudy.ukpt

import feature.ukpt.ukptClientDependencies
import io.ktor.client.engine.HttpClientEngine
import kotlinx.browser.window
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import platform.network.ServerEndpointConfig
import platform.network.networkDependencies
import platform.network.platformHttpClientEngine

/**
 * The Compose HTML client's dependency graph. It lists the same feature modules as
 * :app:client:common's `clientDependencies`, which this module can't depend on because it is
 * Compose UI; `FeatureRules.DependencyModule.registeredInEveryClientShell` checks both lists.
 */
val clientDependencies: List<Module> = listOf(
    module {
        single<HttpClientEngine> { platformHttpClientEngine() } onClose { it?.close() }
        // The page's own origin: the webpack dev server proxies /services and /urpc to the server.
        single<ServerEndpointConfig> { ServerEndpointConfig(baseUrl = window.location.origin) }
    },
    networkDependencies,
    ukptClientDependencies,
)
