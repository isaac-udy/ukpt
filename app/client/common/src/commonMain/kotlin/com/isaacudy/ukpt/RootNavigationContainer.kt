package com.isaacudy.ukpt

import androidx.compose.runtime.Composable
import dev.enro.NavigationKey
import dev.enro.ui.NavigationContainerState

/**
 * The application's root navigation container, starting on [root].
 *
 * On web the container starts on the destination whose `@NavigationPath` matches the address bar,
 * when one does, and installs Enro's web history plugin: browser back and forward move through the
 * container tree, and the address bar shows the path of the deepest active destination that has one.
 */
@Composable
internal expect fun rememberRootNavigationContainer(root: NavigationKey): NavigationContainerState
