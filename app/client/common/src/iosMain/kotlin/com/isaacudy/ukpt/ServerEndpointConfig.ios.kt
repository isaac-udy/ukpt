package com.isaacudy.ukpt

import platform.network.ServerEndpointConfig

internal actual fun serverEndpointConfig(): ServerEndpointConfig =
    ServerEndpointConfig(baseUrl = "http://localhost:8080")
