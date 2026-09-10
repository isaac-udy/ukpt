package app.client

import androidx.compose.runtime.Composable
import feature.shop.client.domain.GetBoundNotConsumed
import feature.shop.client.domain.GetResolvedByInjectDelegate
import feature.shop.client.domain.GetResolvedFromKoin
import feature.shop.client.domain.GetResolvedInCompose
import org.koin.compose.koinInject
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.dsl.bind
import org.koin.dsl.module

val shopClientModule = module {
    single { GetBoundNotConsumed { "" } } bind GetBoundNotConsumed::class
}

fun startShopClient(koin: Koin) {
    koin.get<GetResolvedFromKoin>()
}

class ShopStartup : KoinComponent {
    private val resolved: GetResolvedByInjectDelegate by inject<GetResolvedByInjectDelegate>()
}

@Composable
fun ShopEntryPoint() {
    val resolved = koinInject<GetResolvedInCompose>()
}
