package feature.ukpt

import dev.isaacudy.udytils.urpc.UrpcClientFactory
import feature.ukpt.client.data.GreetingRepository
import feature.ukpt.client.domain.FlowOfGreetingSummary
import feature.ukpt.client.domain.Greet
import feature.ukpt.client.domain.UpdateGreetings
import feature.ukpt.client.ui.ConfirmResetViewModel
import feature.ukpt.client.ui.UkptViewModel
import feature.ukpt.server.services.GreetingService
import feature.ukpt.server.services.create
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val ukptClientDependencies = module {
    single<GreetingService> { get<UrpcClientFactory>().create() }

    singleOf(::GreetingRepository)
    single<FlowOfGreetingSummary> { get<GreetingRepository>().flowOfGreetingSummary }
    single<Greet> { get<GreetingRepository>().greet }
    single<UpdateGreetings> { get<GreetingRepository>().updateGreetings }

    viewModelOf(::UkptViewModel)
    viewModelOf(::ConfirmResetViewModel)
}
