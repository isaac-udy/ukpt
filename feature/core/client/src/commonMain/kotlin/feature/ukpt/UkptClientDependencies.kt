package feature.ukpt

import feature.ukpt.client.domain.FlowOfGreetingHistory
import feature.ukpt.client.domain.FlowOfGreetingHistoryImpl
import feature.ukpt.client.domain.FlowOfGreetingSummary
import feature.ukpt.client.domain.FlowOfGreetingSummaryImpl
import feature.ukpt.client.domain.FlowOfGreetings
import feature.ukpt.client.domain.FlowOfGreetingsImpl
import feature.ukpt.client.domain.FlowOfLatestGreeting
import feature.ukpt.client.domain.FlowOfLatestGreetingImpl
import feature.ukpt.client.domain.GetGreeting
import feature.ukpt.client.domain.GetGreetingImpl
import feature.ukpt.client.ui.ConfirmResetViewModel
import feature.ukpt.client.ui.UkptViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val ukptClientDependencies = module {
    singleOf(::FlowOfGreetingsImpl) bind FlowOfGreetings::class
    singleOf(::FlowOfLatestGreetingImpl) bind FlowOfLatestGreeting::class
    singleOf(::FlowOfGreetingHistoryImpl) bind FlowOfGreetingHistory::class
    singleOf(::FlowOfGreetingSummaryImpl) bind FlowOfGreetingSummary::class
    singleOf(::GetGreetingImpl) bind GetGreeting::class

    viewModelOf(::UkptViewModel)
    viewModelOf(::ConfirmResetViewModel)
}
