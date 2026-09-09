A setting that varies between deployments, carried by a configuration the dependency module assembles; the clock is a dependency the graph supplies, and the page size a setting fixed for every deployment:

```kotlin
// feature/shop/client/data/CatalogConfig.kt
package feature.shop.client.data

internal data class CatalogConfig(val baseUrl: String)

// feature/shop/client/data/CatalogRepository.kt
package feature.shop.client.data

internal class CatalogRepository(
    private val client: HttpClient,
    private val clock: Clock,
    private val config: CatalogConfig,
) {
    private val pageSize = 50
    // …
}

// feature/shop/shopClientDependencies.kt
val shopClientDependencies = module {
    single { CatalogConfig(baseUrl = "https://api.example.com") }
    singleOf(::CatalogRepository)
}
```

Not `clock: Clock = Clock.System` or `pageSize: Int = 50` on the constructor (`ProjectRules.injectableConstructorsHaveNoDefaults`), and not `single { CatalogRepository(get(), get(), CatalogConfig(baseUrl = "…")) }` (`ProjectRules.constructorReferenceBindings`).
