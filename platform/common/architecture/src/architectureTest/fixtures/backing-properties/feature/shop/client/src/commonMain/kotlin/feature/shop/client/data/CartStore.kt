package feature.shop.client.data

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

private val _registry = mutableMapOf<String, String>()
val registry: Map<String, String> get() = _registry

internal class CartStore {
    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items: StateFlow<List<String>> get() = _items

    private val _events = Channel<String>()
    val events: Flow<String> = _events.receiveAsFlow()

    private val _scratch = mutableListOf<String>()

    fun add(item: String) {
        _items.value = _items.value + item
    }
}

internal class PromoStore {
    val codes: List<String>
        field = mutableListOf()

    var lastAppliedAt: Long? = null
        private set

    fun apply(code: String) {
        codes.add(code)
        lastAppliedAt = 0L
    }

    object Defaults {
        private val _limits = mutableListOf(1)
        val limits: List<Int> get() = _limits
    }
}
