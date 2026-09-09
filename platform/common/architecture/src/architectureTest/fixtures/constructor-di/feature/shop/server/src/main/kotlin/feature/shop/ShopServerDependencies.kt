package feature.shop

import feature.shop.server.data.InvoicesClient
import feature.shop.server.data.OrdersConfig
import feature.shop.server.data.OrdersRepository
import feature.shop.server.data.ReportsClient
import feature.shop.server.domain.GetOrders
import feature.shop.server.domain.ReconcileOrders
import feature.shop.server.domain.ReconcileOrdersImpl
import kotlin.time.Clock
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val shopServerDependencies = module {
    single<Clock> { Clock.System }
    single { OrdersConfig(region = "eu") }
    singleOf(::OrdersRepository)
    single<GetOrders> { get<OrdersRepository>().getOrders }
    singleOf(::ReconcileOrdersImpl) bind ReconcileOrders::class
    single { ReportsClient(bucket = "reports") }
    singleOf(::InvoicesClient)
}
