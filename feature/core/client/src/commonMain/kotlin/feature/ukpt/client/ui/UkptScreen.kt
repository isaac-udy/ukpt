package feature.ukpt.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.enro.annotations.NavigationDestination
import dev.isaacudy.udytils.state.AsyncState
import dev.isaacudy.udytils.state.isLoading
import feature.ukpt.Greeting
import feature.ukpt.client.domain.GreetingSummary
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
        onRetry = viewModel::onRetryClicked,
        onReset = viewModel::onResetRequested,
    )
}

@Composable
internal fun UkptScreenContent(
    state: UkptState,
    onGreet: () -> Unit,
    onRetry: () -> Unit,
    onReset: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UkptTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        when (val summary = state.greetingSummary) {
            is AsyncState.Idle,
            is AsyncState.Loading -> {
                CircularProgressIndicator(color = UkptTheme.colors.accent)
            }
            is AsyncState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(UkptSpacing.md),
                ) {
                    Text(
                        text = "Something went wrong",
                        style = UkptTheme.typography.title,
                        color = UkptTheme.colors.onSurface,
                    )
                    UkptButton(
                        label = "Retry",
                        onClick = onRetry,
                    )
                }
            }
            is AsyncState.Success -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(UkptSpacing.md),
                ) {
                    Text(
                        text = summary.data.latestGreeting?.text ?: "No greetings yet",
                        style = UkptTheme.typography.title,
                        color = UkptTheme.colors.onSurface,
                    )
                    Text(
                        text = "${summary.data.greetingHistory.size} greetings in history",
                        style = UkptTheme.typography.caption,
                        color = UkptTheme.colors.onSurfaceVariant,
                    )
                    UkptButton(
                        label = if (state.greetAction.isLoading()) "Greeting…" else "Greet",
                        onClick = onGreet,
                        enabled = !state.greetAction.isLoading(),
                    )
                    if (state.greetAction is AsyncState.Error) {
                        Text(
                            text = "Greet failed",
                            style = UkptTheme.typography.caption,
                            color = UkptTheme.colors.accent,
                        )
                    }
                    UkptButton(
                        label = "Reset",
                        onClick = onReset,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
internal fun UkptScreenLoadingPreview() {
    UkptPreviewFrame(colors = UkptColors.Light) {
        UkptScreenContent(
            state = UkptState(),
            onGreet = {},
            onRetry = {},
            onReset = {},
        )
    }
}

@Preview
@Composable
internal fun UkptScreenErrorPreview() {
    UkptPreviewFrame(colors = UkptColors.Light) {
        UkptScreenContent(
            state = UkptState(
                greetingSummary = AsyncState.Error(RuntimeException("Connection failed")),
            ),
            onGreet = {},
            onRetry = {},
            onReset = {},
        )
    }
}

@Preview
@Composable
internal fun UkptScreenSuccessPreview() {
    UkptPreviewFrame(colors = UkptColors.Light) {
        UkptScreenContent(
            state = UkptState(
                greetingSummary = AsyncState.Success(
                    GreetingSummary(
                        latestGreeting = Greeting(text = "Hello, ukpt!"),
                        greetingHistory = listOf(
                            Greeting(text = "Hello, ukpt!"),
                            Greeting(text = "Hi there"),
                        ),
                    ),
                ),
            ),
            onGreet = {},
            onRetry = {},
            onReset = {},
        )
    }
}

@Preview
@Composable
internal fun UkptScreenEmptySuccessPreview() {
    UkptPreviewFrame(colors = UkptColors.Light) {
        UkptScreenContent(
            state = UkptState(
                greetingSummary = AsyncState.Success(
                    GreetingSummary(
                        latestGreeting = null,
                        greetingHistory = emptyList(),
                    ),
                ),
            ),
            onGreet = {},
            onRetry = {},
            onReset = {},
        )
    }
}
