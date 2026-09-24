package feature.ukpt

import feature.ukpt.server.data.GreetingRepository
import feature.ukpt.server.domain.FlowOfGreetingSummary
import feature.ukpt.server.domain.Greet
import feature.ukpt.server.domain.GreetImpl
import feature.ukpt.server.domain.UpdateGreetings
import feature.ukpt.server.web.UkptRoutes
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import platform.server.web.WebRoutes

val ukptServerDependencies = module {
    singleOf(::GreetingRepository)
    single<FlowOfGreetingSummary> { get<GreetingRepository>().flowOfGreetingSummary }
    single<UpdateGreetings> { get<GreetingRepository>().updateGreetings }

    singleOf(::GreetImpl) bind Greet::class

    singleOf(::UkptRoutes) bind WebRoutes::class
}
