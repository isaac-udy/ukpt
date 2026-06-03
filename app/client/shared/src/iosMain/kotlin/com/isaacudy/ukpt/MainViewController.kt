package com.isaacudy.ukpt

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

private var navigationInstalled = false

@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController {
    if (!navigationInstalled) {
        installUkptNavigation()
        navigationInstalled = true
    }
    return ComposeUIViewController { App() }
}

internal expect fun installUkptNavigation()
