package feature.ukpt.client.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.enro.annotations.NavigationDestination
import dev.enro.html.htmlDialog
import dev.enro.ui.NavigationDestinationProvider
import dev.enro.ui.navigationDestinationContent
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

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
    H2 { Text("Reset greeting count?") }
    P { Text("The greeting count will be set back to zero.") }
    Div(attrs = { classes("ukpt-dialog-actions") }) {
        Button(attrs = {
            classes("secondary")
            onClick { onDismiss() }
        }) { Text("Cancel") }
        Button(attrs = { onClick { onConfirm() } }) { Text("Reset") }
    }
}
