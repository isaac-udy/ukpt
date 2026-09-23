package com.isaacudy.ukpt

import androidx.compose.runtime.Composable
import dev.enro.NavigationKey
import dev.enro.asInstance
import dev.enro.backstackOf
import dev.enro.ui.NavigationContainerState
import dev.enro.ui.rememberNavigationContainer

@Composable
internal actual fun rememberRootNavigationContainer(root: NavigationKey): NavigationContainerState =
    rememberNavigationContainer(backstack = backstackOf(root.asInstance()))
