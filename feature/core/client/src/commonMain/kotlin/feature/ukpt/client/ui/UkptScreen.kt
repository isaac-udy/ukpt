package feature.ukpt.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.enro.annotations.NavigationDestination
import platform.design.UkptColors
import platform.design.UkptPreviewFrame
import platform.design.UkptSpacing
import platform.design.UkptTheme
import platform.design.components.UkptButton

@Composable
@NavigationDestination(UkptDestination::class)
fun UkptScreen(
    viewModel: UkptViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    UkptScreenContent(
        state = state,
        onGreet = viewModel::onGreetClicked,
    )
}

/**
 * Stateless screen content: it renders [state] and reports intent through [onGreet].
 *
 * Every colour, dimension and text style comes from [UkptTheme] rather than a literal — see
 * `platform/client/design/design-system/principles.md`.
 */
@Composable
internal fun UkptScreenContent(
    state: UkptState,
    onGreet: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UkptTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UkptSpacing.md),
        ) {
            Text(
                text = state.message,
                style = UkptTheme.typography.title,
                color = UkptTheme.colors.onSurface,
            )
            Text(
                text = "Greeted ${state.greetings} times",
                style = UkptTheme.typography.caption,
                color = UkptTheme.colors.onSurfaceVariant,
            )
            UkptButton(
                label = "Greet",
                onClick = onGreet,
            )
        }
    }
}

@Preview
@Composable
internal fun UkptScreenPreview() {
    // The frame sizes the golden to the primary viewport and pins the palette, so the snapshot
    // reads as a deterministic device screenshot rather than a render on the harness canvas.
    UkptPreviewFrame(colors = UkptColors.Light) {
        UkptScreenContent(
            state = UkptState(),
            onGreet = {},
        )
    }
}
