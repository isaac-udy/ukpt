package platform.testing.subcutaneous

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.enro.NavigationKey
import dev.enro.navigationHandle
import dev.enro.requestClose
import dev.isaacudy.udytils.state.ViewModelState
import dev.isaacudy.udytils.state.viewModelState
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import platform.testing.SubcutaneousTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

@SubcutaneousTest
class SubcutaneousHarnessTest {

    @Test
    fun `a ViewModel reads a binding from the client edge`() = subcutaneous(counterGraph(), edges = { client { single { FakeNextCount(3) } } }) {
        val screen = viewModel<FakeCounterViewModel, FakeCounterKey>(FakeCounterKey)

        assertEquals(3, screen.viewModel.state.awaitMatching { it.count != null }.count)
    }

    @Test
    fun `a test edge replaces a host edge`() = subcutaneous(counterGraphWithHostEdges(), edges = { client { single { FakeNextCount(2) } } }) {
        val screen = viewModel<FakeCounterViewModel, FakeCounterKey>(FakeCounterKey)

        assertEquals(2, screen.viewModel.state.awaitMatching { it.count != null }.count)
    }

    @Test
    fun `a ViewModel closes its navigation handle`() = subcutaneous(counterGraphWithHostEdges()) {
        val screen = viewModel<FakeCounterViewModel, FakeCounterKey>(FakeCounterKey)

        screen.viewModel.onDoneClicked()

        screen.navigation.awaitClosed()
    }

    @Test
    fun `inspectClient reads the calls a fake at the client edge received`() = subcutaneous(counterGraphWithHostEdges()) {
        viewModel<FakeCounterViewModel, FakeCounterKey>(FakeCounterKey)

        inspectClient { client<FakeNextCount>().calls.assertSingle() }
    }

    @Test
    fun `inspectServer fails without a server`() = subcutaneous(counterGraphWithHostEdges()) {
        assertFailsWith<IllegalStateException> { inspectServer { } }
    }

    @Test
    fun `awaitMatching fails after its timeout in real time`() = subcutaneous(counterGraphWithHostEdges()) {
        val screen = viewModel<FakeCounterViewModel, FakeCounterKey>(FakeCounterKey)
        val start = TimeSource.Monotonic.markNow()

        assertFailsWith<AssertionError> { screen.viewModel.state.awaitMatching(timeout = 300.milliseconds) { it.count == -1 } }

        assertTrue(start.elapsedNow() >= 300.milliseconds)
    }
}

@Serializable
internal object FakeCounterKey : NavigationKey

internal class FakeNextCount(private val count: Int) {
    val calls = Recording<Unit>()

    suspend fun next(): Int {
        calls.record(Unit)
        return count
    }
}

internal data class FakeCounterState(val count: Int? = null)

internal class FakeCounterViewModel(private val nextCount: FakeNextCount) : ViewModel() {
    private val navigation by navigationHandle<FakeCounterKey>()

    val state: ViewModelState<FakeCounterState> = viewModelState(FakeCounterState())

    init {
        viewModelScope.launch { state.update { copy(count = nextCount.next()) } }
    }

    fun onDoneClicked() {
        navigation.requestClose()
    }
}
