package com.isaacudy.ukpt

import feature.ukpt.ukptClientDependencies
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import platform.network.ServerEndpointConfig
import platform.network.networkDependencies
import platform.network.platformHttpClientEngine

/**
 * The client's whole dependency graph. Each feature's `[name]ClientDependencies` is added here,
 * so `ClientDependenciesTest` verifies the same list `App` installs.
 */
val clientDependencies: List<Module> = listOf(
    module {
        single<HttpClientEngine> { platformHttpClientEngine() } onClose { it?.close() }
        single<ServerEndpointConfig> { serverEndpointConfig() }
    },
    networkDependencies,
    ukptClientDependencies,
)

/** The dev server's address as this platform reaches it. */
internal expect fun serverEndpointConfig(): ServerEndpointConfig
