package feature.ukpt.client.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.enro.annotations.NavigationDestination
import dev.enro.complete
import dev.enro.requestClose
import dev.enro.ui.NavigationDestinationProvider
import dev.enro.ui.navigationDestination
import dev.enro.ui.scenes.directOverlayWithFade
import platform.design.UkptColors
import platform.design.UkptPreviewFrame
import platform.design.UkptTheme

@NavigationDestination(ConfirmResetDestination::class)
val confirmResetDialogDestination: NavigationDestinationProvider<ConfirmResetDestination> =
    navigationDestination(metadata = { directOverlayWithFade() }) {
        ConfirmResetDialogScreenContent(
            onConfirm = { navigation.complete(true) },
            onDismiss = { navigation.requestClose() },
        )
    }

@Composable
internal fun ConfirmResetDialogScreenContent(
    onConfirm: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Reset greeting count?",
                style = UkptTheme.typography.title,
            )
        },
        text = {
            Text(
                text = "The greeting count will be set back to zero.",
                style = UkptTheme.typography.body,
                color = UkptTheme.colors.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Reset",
                    color = UkptTheme.colors.accent,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

@Preview
@Composable
internal fun ConfirmResetDialogPreview() {
    UkptPreviewFrame(colors = UkptColors.Light) {
        ConfirmResetDialogScreenContent()
    }
}
