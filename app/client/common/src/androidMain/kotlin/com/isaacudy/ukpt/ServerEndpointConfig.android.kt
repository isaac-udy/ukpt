package com.isaacudy.ukpt

import platform.network.ServerEndpointConfig

// 10.0.2.2 is the emulator's alias for the host machine's loopback interface.
internal actual fun serverEndpointConfig(): ServerEndpointConfig =
    ServerEndpointConfig(baseUrl = "http://10.0.2.2:8080")
