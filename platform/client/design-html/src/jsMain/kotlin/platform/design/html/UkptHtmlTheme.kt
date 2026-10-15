package platform.design.html

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.isaacudy.udytils.ui.html.LocalUdytilsHtmlClasses
import dev.isaacudy.udytils.ui.html.UdytilsHtmlClasses

/**
 * Wraps the Compose HTML client's content, as `UkptTheme` wraps the Compose UI clients'. Gives
 * udytils' confirmation and error dialogs the `ukpt-design.css` classes.
 */
@Composable
fun UkptHtmlTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalUdytilsHtmlClasses provides udytilsHtmlClasses, content = content)
}

private val udytilsHtmlClasses = UdytilsHtmlClasses(
    dialogContent = listOf(UkptHtmlLayout.dialogContent),
    title = listOf(UkptHtmlTypography.title),
    body = listOf(UkptHtmlTypography.body, UkptHtmlColors.onSurfaceVariant),
    caption = listOf(UkptHtmlTypography.caption, UkptHtmlColors.onSurfaceVariant),
    actions = listOf(UkptHtmlLayout.dialogActions),
    confirmButton = listOf("ukpt-button", UkptHtmlButtonVariant.Primary.className),
    destructiveButton = listOf("ukpt-button", "ukpt-button-destructive"),
    dismissButton = listOf("ukpt-button", UkptHtmlButtonVariant.Ghost.className),
)
