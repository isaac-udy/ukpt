package platform.testing.subcutaneous

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import dev.enro.NavigationKey
import dev.enro.test.putNavigationHandleForViewModel
import org.koin.core.Koin
import kotlin.reflect.KClass

/** The body of a [subcutaneous] test: seed state, open ViewModels, call services. */
class SubcutaneousScope internal constructor(
    private val scenario: Scenario,
    @PublishedApi internal val clientKoin: Koin,
    private val viewModels: ViewModelStore,
    private val services: ((Koin, KClass<*>) -> Any)?,
) {
    private var opened = 0

    /** Runs a scenario: before the first ViewModel opens or mid-test. */
    suspend fun <T> given(block: suspend Scenario.() -> T): T = scenario.block()

    /** Opens [key]'s ViewModel from the client graph, with a test navigation handle for [key]. */
    inline fun <reified VM : ViewModel, K : NavigationKey> viewModel(key: K): Screen<VM, K> = viewModel<VM, K>(key) {}

    /** Opens [key]'s ViewModel as [viewModel] does, with [metadata] set on the key's instance first. */
    inline fun <reified VM : ViewModel, K : NavigationKey> viewModel(
        key: K,
        metadata: NavigationKey.Metadata.() -> Unit,
    ): Screen<VM, K> {
        val navigation = putNavigationHandleForViewModel<VM, K>(key)
        navigation.instance.metadata.metadata()
        return Screen(createViewModel(VM::class), navigation)
    }

    /** The server's service [S], called through the client transport against the in-process server. */
    inline fun <reified S : Any> service(): S = service(S::class)

    @PublishedApi
    internal fun <VM : ViewModel> createViewModel(type: KClass<VM>): VM {
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = clientKoin.get(modelClass)
        }
        return ViewModelProvider.create(viewModels, factory)["viewModel-${opened++}", type]
    }

    @PublishedApi
    internal fun <S : Any> service(type: KClass<S>): S {
        val build = services ?: error("service<${type.simpleName}>() needs a test graph with a server.")
        return type.java.cast(build(clientKoin, type))
    }
}
