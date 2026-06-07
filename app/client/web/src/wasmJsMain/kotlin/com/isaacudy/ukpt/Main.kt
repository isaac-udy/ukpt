package com.isaacudy.ukpt

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.enro.ui.EnroBrowserContent
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    UkptNavigation.installNavigationController(document)
    ComposeViewport(document.body!!) {
        EnroBrowserContent {
            App()
        }
    }
}
