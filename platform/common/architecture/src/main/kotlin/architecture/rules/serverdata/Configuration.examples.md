A setting that varies between deployments, carried by a configuration the dependency module assembles; the clock is a dependency the graph supplies, and the retry count a setting fixed for every deployment:

```kotlin
// feature/orders/server/data/InvoicesConfig.kt
package feature.orders.server.data

internal data class InvoicesConfig(val bucket: String)

// feature/orders/server/data/InvoicesClient.kt
package feature.orders.server.data

internal class InvoicesClient(
    private val storage: ObjectStorage,
    private val clock: Clock,
    private val config: InvoicesConfig,
) {
    private val maxRetries = 3
    val getInvoice = GetInvoice { id -> /* … */ }
}

// feature/orders/ordersServerDependencies.kt
val ordersServerDependencies = module {
    single { InvoicesConfig(bucket = System.getenv("INVOICES_BUCKET") ?: "invoices") }
    singleOf(::InvoicesClient)
    single<GetInvoice> { get<InvoicesClient>().getInvoice }
}
```

Not `clock: Clock = Clock.System` or `maxRetries: Int = 3` on the constructor (`ProjectRules.injectableConstructorsHaveNoDefaults`), not `single { InvoicesClient(get(), get(), InvoicesConfig(bucket = "invoices")) }` (`ProjectRules.constructorReferenceBindings`), and not `private val config = InvoicesConfig(bucket = "invoices")` inside the client (`ServerData.Configuration.assembledAtTheCompositionBoundary`).
