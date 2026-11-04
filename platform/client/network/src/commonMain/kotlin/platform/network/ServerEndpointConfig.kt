package platform.network

/** Where the client reaches the server: protocol, host and port, with no trailing slash. */
data class ServerEndpointConfig(
    val baseUrl: String,
)
