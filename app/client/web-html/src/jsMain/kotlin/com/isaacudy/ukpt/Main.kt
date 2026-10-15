package com.isaacudy.ukpt

import dev.enro.annotations.ExperimentalEnroApi
import dev.enro.asInstance
import dev.enro.backstackOf
import dev.enro.html.EnroHtmlContent
import dev.enro.html.HtmlNavigationDisplay
import dev.enro.ui.InstallWebHistoryPlugin
import dev.enro.ui.rememberInitialBackstackFromUrl
import dev.enro.ui.rememberNavigationContainer
import feature.ukpt.client.ui.UkptDestination
import kotlinx.browser.document
import org.jetbrains.compose.web.renderComposable
import org.koin.core.context.startKoin
import platform.design.html.UkptHtmlTheme

@OptIn(ExperimentalEnroApi::class)
fun main() {
    startKoin { modules(clientDependencies) }
    UkptHtmlNavigation.installNavigationController(document)
    renderComposable(rootElementId = "root") {
        EnroHtmlContent {
            UkptHtmlTheme {
                val container = rememberNavigationContainer(
                    backstack = rememberInitialBackstackFromUrl { backstackOf(UkptDestination.asInstance()) },
                )
                InstallWebHistoryPlugin(container)
                HtmlNavigationDisplay(container)
            }
        }
    }
}
