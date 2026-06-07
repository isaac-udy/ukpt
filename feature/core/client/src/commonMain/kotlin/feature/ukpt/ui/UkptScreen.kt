package feature.ukpt.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.enro.annotations.NavigationDestination

@Composable
@NavigationDestination(UkptDestination::class)
fun UkptScreen(
    viewModel: UkptViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    UkptScreenContent(state)
}

@Composable
internal fun UkptScreenContent(state: UkptState) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(state.message)
        }
    }
}
