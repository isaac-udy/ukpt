package platform.testing.subcutaneous

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

internal actual fun mainDispatcher(): CoroutineDispatcher =
    Executors.newSingleThreadScheduledExecutor { Thread(it, "subcutaneous-main").apply { isDaemon = true } }
        .asCoroutineDispatcher()
