package com.isaacudy.ukpt

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.enro.asBackstack
import dev.enro.asInstance
import dev.enro.ui.NavigationDisplay
import dev.enro.ui.rememberNavigationContainer
import feature.ukpt.ukptClientDependencies
import feature.ukpt.ui.UkptDestination
import org.koin.compose.KoinApplication
import platform.design.ProvideUkptViewport
import platform.design.UkptTheme

@Composable
fun App() {
    // Start Koin for the composition. The Enro ViewModel factory (in UkptNavigation)
    // resolves ViewModels from this Koin scope.
    KoinApplication(application = { modules(ukptClientDependencies) }) {
        // The design system is installed once, here, above navigation — so every destination
        // renders inside it and no screen wraps a theme of its own. UkptTheme wraps a
        // MaterialTheme derived from the tokens, so raw material internals inherit them too.
        UkptTheme {
            ProvideUkptViewport {
                val rootContainer = rememberNavigationContainer(
                    backstack = listOf(UkptDestination.asInstance()).asBackstack(),
                )
                NavigationDisplay(
                    state = rootContainer,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
