package feature.ukpt

import dev.isaacudy.udytils.urpc.koin.UrpcCall
import dev.isaacudy.udytils.urpc.koin.bindService
import feature.ukpt.server.data.GreetingRepository
import feature.ukpt.server.data.storage.GreetingStorage
import feature.ukpt.server.domain.FlowOfGreetingHistory
import feature.ukpt.server.domain.Greet
import feature.ukpt.server.domain.GreetImpl
import feature.ukpt.server.domain.UpdateGreetingHistory
import feature.ukpt.server.services.GreetingService
import feature.ukpt.server.services.GreetingServiceImpl
import feature.ukpt.server.services.GreetingServiceUrpcBinding
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val ukptServerDependencies = module {
    singleOf(::GreetingStorage)
    singleOf(::GreetingRepository)
    single<FlowOfGreetingHistory> { get<GreetingRepository>().flowOfGreetingHistory }
    single<UpdateGreetingHistory> { get<GreetingRepository>().updateGreetingHistory }

    singleOf(::GreetImpl) bind Greet::class

    scope<UrpcCall> {
        scopedOf(::GreetingServiceImpl)
            .bind(GreetingService::class)
            .bindService(::GreetingServiceUrpcBinding)
    }
}
