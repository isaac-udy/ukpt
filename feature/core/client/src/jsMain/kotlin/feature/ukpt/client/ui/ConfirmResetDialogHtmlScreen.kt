package feature.ukpt.client.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.enro.annotations.NavigationDestination
import dev.enro.html.htmlDialog
import dev.enro.ui.NavigationDestinationProvider
import dev.enro.ui.navigationDestinationContent
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import platform.design.html.UkptHtmlButton
import platform.design.html.UkptHtmlButtonVariant
import platform.design.html.UkptHtmlColors
import platform.design.html.UkptHtmlLayout
import platform.design.html.UkptHtmlTypography

@NavigationDestination.PlatformOverride(ConfirmResetDestination::class)
val confirmResetDialogHtmlDestination: NavigationDestinationProvider<ConfirmResetDestination> =
    navigationDestinationContent(metadata = { htmlDialog() }) {
        val viewModel: ConfirmResetViewModel = viewModel()
        ConfirmResetDialogHtmlScreenContent(
            onConfirm = viewModel::onConfirm,
            onDismiss = viewModel::onDismiss,
        )
    }

@Composable
internal fun ConfirmResetDialogHtmlScreenContent(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Div(attrs = { classes(UkptHtmlLayout.dialogContent) }) {
        H2(attrs = { classes(UkptHtmlTypography.title) }) { Text("Reset greeting count?") }
        P(attrs = { classes(UkptHtmlTypography.body, UkptHtmlColors.onSurfaceVariant) }) {
            Text("The greeting count will be set back to zero.")
        }
        Div(attrs = { classes(UkptHtmlLayout.dialogActions) }) {
            UkptHtmlButton(label = "Cancel", onClick = onDismiss, variant = UkptHtmlButtonVariant.Ghost)
            UkptHtmlButton(label = "Reset", onClick = onConfirm)
        }
    }
}
