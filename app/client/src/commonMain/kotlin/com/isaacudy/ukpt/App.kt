package com.isaacudy.ukpt

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.enro.asBackstack
import dev.enro.asInstance
import dev.enro.ui.NavigationDisplay
import dev.enro.ui.rememberNavigationContainer
import feature.ukpt.UkptDestination

@Composable
fun App() {
    MaterialTheme {
        val rootContainer = rememberNavigationContainer(
            backstack = listOf(UkptDestination.asInstance()).asBackstack(),
        )
        NavigationDisplay(
            state = rootContainer,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
