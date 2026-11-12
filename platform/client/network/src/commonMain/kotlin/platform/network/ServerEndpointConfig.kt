package platform.network

/**
 * The URL the client calls the server's urpc services at: protocol, host and port, then the server's
 * `ServerHostConfig.urpcRootPath` when it sets one. No trailing slash.
 */
data class ServerEndpointConfig(
    val baseUrl: String,
)
