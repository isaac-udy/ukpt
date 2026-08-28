package feature.ukpt

import feature.ukpt.client.data.GreetingRepository
import feature.ukpt.client.domain.FlowOfGreetingHistory
import feature.ukpt.client.domain.FlowOfGreetingSummary
import feature.ukpt.client.domain.FlowOfGreetingSummaryImpl
import feature.ukpt.client.domain.FlowOfGreetings
import feature.ukpt.client.domain.FlowOfGreetingsImpl
import feature.ukpt.client.domain.FlowOfLatestGreeting
import feature.ukpt.client.domain.GetGreeting
import feature.ukpt.client.domain.ResetGreetings
import feature.ukpt.client.ui.ConfirmResetViewModel
import feature.ukpt.client.ui.UkptViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val ukptClientDependencies = module {
    singleOf(::GreetingRepository)
    single<FlowOfLatestGreeting> { get<GreetingRepository>().flowOfLatestGreeting }
    single<FlowOfGreetingHistory> { get<GreetingRepository>().flowOfGreetingHistory }
    single<GetGreeting> { get<GreetingRepository>().getGreeting }
    single<ResetGreetings> { get<GreetingRepository>().resetGreetings }

    singleOf(::FlowOfGreetingsImpl) bind FlowOfGreetings::class
    singleOf(::FlowOfGreetingSummaryImpl) bind FlowOfGreetingSummary::class

    viewModelOf(::UkptViewModel)
    viewModelOf(::ConfirmResetViewModel)
}
