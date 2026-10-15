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
import org.koin.mp.KoinPlatform
import kotlin.reflect.KClass

/**
 * The navigation component for the Compose HTML client, with the same Koin ViewModel factory as
 * `UkptNavigation`. Koin is read from the global instance because koin-compose depends on Compose UI.
 */
@NavigationComponent
object UkptHtmlNavigation : NavigationComponentConfiguration(
    module = createNavigationModule {
        decorator {
            NavigationDestinationDecorator(
                onPop = {},
                decorate = { destination ->
                    val localViewModelStoreOwner = requireNotNull(LocalViewModelStoreOwner.current)
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
                                    ): T = KoinPlatform.getKoin().get(modelClass)
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
