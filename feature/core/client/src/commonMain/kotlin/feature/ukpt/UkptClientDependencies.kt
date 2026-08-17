package feature.ukpt

import feature.ukpt.client.ui.ConfirmResetViewModel
import feature.ukpt.client.ui.UkptViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin module for the (placeholder) core client feature.
 *
 * Registering the ViewModel here lets the Enro ViewModel factory (installed in
 * [com.isaacudy.ukpt.UkptNavigation]) create it via `scope.get(modelClass)`.
 * This is required on wasmJs/JS, where there is no reflection and the default
 * androidx.lifecycle factory cannot instantiate a ViewModel.
 */
val ukptClientDependencies = module {
    viewModelOf(::UkptViewModel)
    viewModelOf(::ConfirmResetViewModel)
}
