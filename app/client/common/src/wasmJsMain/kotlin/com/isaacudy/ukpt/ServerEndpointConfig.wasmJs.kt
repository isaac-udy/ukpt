package com.isaacudy.ukpt

import kotlinx.browser.window
import platform.network.ServerEndpointConfig

// The page's own origin: the webpack dev server proxies /services and /urpc to the server.
internal actual fun serverEndpointConfig(): ServerEndpointConfig =
    ServerEndpointConfig(baseUrl = window.location.origin)
