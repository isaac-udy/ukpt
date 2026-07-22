package com.isaacudy.ukpt

import androidx.compose.ui.window.application
import dev.enro.platform.desktop.GenericRootWindow
import dev.enro.platform.desktop.RootWindow
import dev.enro.platform.desktop.openWindow
import dev.enro.ui.EnroApplicationContent

/**
 * The desktop entry point.
 *
 * Hosts [App] in Enro's [GenericRootWindow] rather than a bare Compose `Window`. `App`'s
 * `rememberNavigationContainer` needs a `RootContext`, and on desktop only a [RootWindow]
 * registered through [openWindow] provides one; [EnroApplicationContent] then renders the windows
 * the controller has registered. A plain `application { Window { App() } }` compiles, but fails at
 * first composition with `IllegalStateException: No RootContext provided`.
 *
 * `WindowConfiguration.onCloseRequest` defaults to closing the window's navigation handle, which
 * ends the application once the last root window is gone.
 */
fun main() {
    val controller = UkptNavigation.installNavigationController(Unit)
    controller.openWindow(
        GenericRootWindow(
            windowConfiguration = {
                RootWindow.WindowConfiguration(title = "ukpt")
            },
        ) {
            App()
        },
    )
    application {
        EnroApplicationContent(controller)
    }
}
