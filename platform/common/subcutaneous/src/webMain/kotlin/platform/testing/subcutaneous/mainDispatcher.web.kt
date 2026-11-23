package platform.testing.subcutaneous

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual fun mainDispatcher(): CoroutineDispatcher = Dispatchers.Default
