package com.isaacudy.ukpt

import androidx.compose.runtime.Composable
import dev.enro.NavigationKey
import dev.enro.annotations.ExperimentalEnroApi
import dev.enro.asInstance
import dev.enro.backstackOf
import dev.enro.ui.InstallWebHistoryPlugin
import dev.enro.ui.NavigationContainerState
import dev.enro.ui.rememberInitialBackstackFromUrl
import dev.enro.ui.rememberNavigationContainer

@OptIn(ExperimentalEnroApi::class)
@Composable
internal actual fun rememberRootNavigationContainer(root: NavigationKey): NavigationContainerState {
    val container = rememberNavigationContainer(
        backstack = rememberInitialBackstackFromUrl { backstackOf(root.asInstance()) },
    )
    InstallWebHistoryPlugin(container)
    return container
}
