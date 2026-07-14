package com.isaacudy.ukpt

import dev.enro.platform.EnroUIViewController
import platform.UIKit.UIViewController

private var navigationInstalled = false

/**
 * The entry point hosted by the iOS application (`app/client/ios`).
 *
 * Uses Enro's [EnroUIViewController], not Compose's `ComposeUIViewController`. On iOS
 * `rememberNavigationContainer` finds its `RootContext` by walking up the parent view controller
 * hierarchy, and only [EnroUIViewController] attaches one. Hosting `App()` in a plain
 * `ComposeUIViewController` compiles, but crashes at launch with "Could not find a RootContext in
 * the parent view controller hierarchy".
 */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController {
    if (!navigationInstalled) {
        installUkptNavigation()
        navigationInstalled = true
    }
    return EnroUIViewController { App() }
}

internal expect fun installUkptNavigation()
