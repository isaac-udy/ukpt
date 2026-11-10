package platform.network

/**
 * Where the client reaches the server's urpc services: protocol, host and port, then the server's
 * `ServerHostConfig.urpcRootPath` when it sets one. No trailing slash.
 */
data class ServerEndpointConfig(
    val baseUrl: String,
)
