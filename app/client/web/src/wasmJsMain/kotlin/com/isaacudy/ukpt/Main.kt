package com.isaacudy.ukpt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.enro.ui.EnroBrowserContent
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    UkptNavigation.installNavigationController(document)
    ComposeViewport(document.body!!) {
        EnroBrowserContent {
            App()
        }
        DismissBootScreenOnFirstFrame()
    }
}

/**
 * Tells the boot screen in `index.html` that Compose has painted, so it fades out onto real content.
 *
 * The boot screen cannot detect this itself: [ComposeViewport] appends its canvas host as soon as
 * `main` runs, but skiko is fetched and the first composition mounted only afterwards. The first
 * [withFrameNanos] resumes at the start of the frame the initial composition is drawn in, the second
 * once that frame has been through the renderer.
 */
@Composable
private fun DismissBootScreenOnFirstFrame() {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        notifyFirstFrameDrawn()
    }
}

/**
 * Sets the flag as well as calling the callback: `App.js` is requested from the head and can reach
 * this before the parser reaches the boot script, which then dismisses on the flag.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun notifyFirstFrameDrawn(): Unit = js(
    "{ window.__appFirstFrame = true; if (window.__onAppFirstFrame) window.__onAppFirstFrame(); }"
)
