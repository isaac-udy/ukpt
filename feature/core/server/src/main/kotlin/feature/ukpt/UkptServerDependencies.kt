package feature.ukpt

import feature.ukpt.server.data.GreetingRepository
import feature.ukpt.server.data.storage.GreetingStorage
import feature.ukpt.server.domain.FlowOfGreetingHistory
import feature.ukpt.server.domain.Greet
import feature.ukpt.server.domain.GreetImpl
import feature.ukpt.server.domain.UpdateGreetingHistory
import feature.ukpt.server.web.UkptRoutes
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import platform.server.web.WebRoutes

val ukptServerDependencies = module {
    singleOf(::GreetingStorage)
    singleOf(::GreetingRepository)
    single<FlowOfGreetingHistory> { get<GreetingRepository>().flowOfGreetingHistory }
    single<UpdateGreetingHistory> { get<GreetingRepository>().updateGreetingHistory }

    singleOf(::GreetImpl) bind Greet::class

    singleOf(::UkptRoutes) bind WebRoutes::class
}
