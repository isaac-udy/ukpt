package feature.ukpt.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.enro.annotations.NavigationDestination
import dev.isaacudy.udytils.state.AsyncState
import dev.isaacudy.udytils.state.isLoading
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.Main
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

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
    Main(attrs = { classes("ukpt-screen") }) {
        when (val summary = state.greetingSummary) {
            is AsyncState.Idle,
            is AsyncState.Loading -> {
                P(attrs = { attr("aria-busy", "true") }) { Text("Loading…") }
            }
            is AsyncState.Error -> {
                H1 { Text("Something went wrong") }
                Button(attrs = { onClick { onRetry() } }) { Text("Retry") }
            }
            is AsyncState.Success -> {
                H1 { Text(summary.data.latest?.text ?: "No greetings yet") }
                P(attrs = { classes("caption") }) {
                    Text("${summary.data.greetings.size} greetings in history")
                }
                Button(
                    attrs = {
                        onClick { onGreet() }
                        if (state.greetAction.isLoading()) disabled()
                    },
                ) {
                    Text(if (state.greetAction.isLoading()) "Greeting…" else "Greet")
                }
                if (state.greetAction is AsyncState.Error) {
                    P(attrs = { classes("error") }) { Text("Greet failed") }
                }
                Button(attrs = { onClick { onReset() } }) { Text("Reset") }
            }
        }
    }
}
