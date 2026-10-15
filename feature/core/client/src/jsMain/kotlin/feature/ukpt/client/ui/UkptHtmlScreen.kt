package feature.ukpt.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.enro.annotations.NavigationDestination
import dev.isaacudy.udytils.state.AsyncState
import dev.isaacudy.udytils.state.isLoading
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.Main
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import platform.design.html.UkptHtmlButton
import platform.design.html.UkptHtmlColors
import platform.design.html.UkptHtmlLayout
import platform.design.html.UkptHtmlTypography

@Composable
@NavigationDestination.PlatformOverride(UkptDestination::class)
fun UkptHtmlScreen(
    viewModel: UkptViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    UkptHtmlScreenContent(
        state = state,
        onGreet = viewModel::onGreetClicked,
        onRetry = viewModel::onRetryClicked,
        onReset = viewModel::onResetRequested,
    )
}

@Composable
internal fun UkptHtmlScreenContent(
    state: UkptState,
    onGreet: () -> Unit,
    onRetry: () -> Unit,
    onReset: () -> Unit,
) {
    Main(attrs = { classes(UkptHtmlLayout.screen) }) {
        when (val summary = state.greetingSummary) {
            is AsyncState.Idle,
            is AsyncState.Loading -> {
                P(attrs = {
                    classes(UkptHtmlTypography.body, UkptHtmlColors.onSurfaceVariant)
                    attr("aria-busy", "true")
                }) { Text("Loading…") }
            }
            is AsyncState.Error -> {
                H1(attrs = { classes(UkptHtmlTypography.title) }) { Text("Something went wrong") }
                UkptHtmlButton(label = "Retry", onClick = onRetry)
            }
            is AsyncState.Success -> {
                H1(attrs = { classes(UkptHtmlTypography.title) }) {
                    Text(summary.data.latest?.text ?: "No greetings yet")
                }
                P(attrs = { classes(UkptHtmlTypography.caption, UkptHtmlColors.onSurfaceVariant) }) {
                    Text("${summary.data.greetings.size} greetings in history")
                }
                UkptHtmlButton(
                    label = if (state.greetAction.isLoading()) "Greeting…" else "Greet",
                    onClick = onGreet,
                    enabled = !state.greetAction.isLoading(),
                )
                if (state.greetAction is AsyncState.Error) {
                    P(attrs = { classes(UkptHtmlTypography.caption, UkptHtmlColors.accent) }) {
                        Text("Greet failed")
                    }
                }
                UkptHtmlButton(label = "Reset", onClick = onReset)
            }
        }
    }
}
