package platform.network

import io.ktor.client.engine.HttpClientEngine

/** CIO on JVM, Android and iOS; the browser's fetch and WebSocket on web. */
expect fun platformHttpClientEngine(): HttpClientEngine
