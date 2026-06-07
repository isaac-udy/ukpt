package com.isaacudy.ukpt

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.enro.annotations.NavigationComponent
import dev.enro.controller.NavigationComponentConfiguration
import dev.enro.controller.createNavigationModule
import dev.enro.navigationHandle
import dev.enro.ui.decorators.NavigationDestinationDecorator
import dev.enro.viewmodel.withNavigationHandle
import org.koin.compose.currentKoinScope
import kotlin.reflect.KClass

@NavigationComponent
object UkptNavigation : NavigationComponentConfiguration(
    module = createNavigationModule {
        // Install a ViewModelProvider.Factory that creates ViewModels via Koin.
        //
        // `viewModel()` resolves a ViewModel through the LocalViewModelStoreOwner's
        // default factory. On JVM/Android the platform default factory can reflectively
        // instantiate a no-arg ViewModel, so this "just works"; on wasmJs/JS there is no
        // reflection, and androidx.lifecycle's default factory throws
        // "Factory.create(String, CreationExtras) is not implemented".
        //
        // This decorator provides a factory that creates each ViewModel from Koin
        // (`scope.get(modelClass)`) and wraps it with `.withNavigationHandle(...)` so the
        // Enro navigation handle is injected into CreationExtras (making `by
        // navigationHandle()` inside the ViewModel work).
        decorator {
            NavigationDestinationDecorator(
                onPop = {},
                decorate = { destination ->
                    val localViewModelStoreOwner = requireNotNull(LocalViewModelStoreOwner.current)
                    val scope = currentKoinScope()
                    val navigationHandle = navigationHandle()
                    val owner = remember {
                        object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
                            override val viewModelStore: ViewModelStore
                                get() = localViewModelStoreOwner.viewModelStore

                            override val defaultViewModelProviderFactory: ViewModelProvider.Factory
                                get() = object : ViewModelProvider.Factory {
                                    override fun <T : ViewModel> create(
                                        modelClass: KClass<T>,
                                        extras: CreationExtras,
                                    ): T = scope.get(modelClass)
                                }.withNavigationHandle(navigationHandle)
                        }
                    }
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides owner,
                    ) {
                        destination.Content()
                    }
                }
            )
        }
    }
)
