package platform.testing.subcutaneous

import androidx.lifecycle.ViewModel
import dev.enro.NavigationKey
import dev.enro.test.TestNavigationHandle

/** A ViewModel from the client graph, and the navigation handle it was opened with. */
class Screen<VM : ViewModel, K : NavigationKey>(
    val viewModel: VM,
    val navigation: TestNavigationHandle<K>,
)
