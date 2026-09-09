package feature.ukpt

import feature.ukpt.client.data.GreetingRepository
import feature.ukpt.client.domain.FlowOfGreetingSummary
import feature.ukpt.client.domain.Greet
import feature.ukpt.client.domain.GreetImpl
import feature.ukpt.client.domain.UpdateGreetings
import feature.ukpt.client.ui.ConfirmResetViewModel
import feature.ukpt.client.ui.UkptViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val ukptClientDependencies = module {
    singleOf(::GreetingRepository)
    single<FlowOfGreetingSummary> { get<GreetingRepository>().flowOfGreetingSummary }
    single<UpdateGreetings> { get<GreetingRepository>().updateGreetings }

    singleOf(::GreetImpl) bind Greet::class

    viewModelOf(::UkptViewModel)
    viewModelOf(::ConfirmResetViewModel)
}
