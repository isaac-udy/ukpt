package com.isaacudy.ukpt

import androidx.compose.runtime.mutableStateOf
import dev.enro.annotations.AdvancedEnroApi
import dev.enro.context.RootContext
import dev.enro.test.fixtures.TestLifecycleAndViewModelStoreOwner

// Installed once per test JVM, as `main()` installs it once per process: Enro has no public uninstall.
private val navigationController by lazy { UkptNavigation.installNavigationController(Unit) }

/**
 * A root context on the application's navigation controller, for `LocalRootContext`. In
 * `runComposeUiTest` no `RootWindow` exists to provide one.
 */
@OptIn(AdvancedEnroApi::class)
fun appRootContext(): RootContext {
    val owner = TestLifecycleAndViewModelStoreOwner()
    return RootContext(
        id = "EndToEndTest",
        parent = Unit,
        controller = navigationController,
        lifecycleOwner = owner,
        viewModelStoreOwner = owner,
        defaultViewModelProviderFactory = owner,
        activeChildId = mutableStateOf(null),
    )
}
