package com.isaacudy.ukpt

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    UkptNavigation.installNavigationController(document)
    ComposeViewport(document.body!!) {
        App()
    }
}
