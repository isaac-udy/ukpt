Registering a urpc service in `:server`, per `FeatureRules.DependencyModule.urpcServiceBinding`:

```kotlin
scope<UrpcCall> {
    scopedOf(::UserProfileServiceImpl)
        .bind(UserProfileService::class)
        .bindService(::UserProfileServiceUrpcBinding)
}
```

Service implementations live in `feature.[name].server.services`, never in the top-level package; only their bindings appear in the dependency module:

```kotlin
// feature.user.server.services.UserServiceImpl.kt (:server)
internal class UserServiceImpl(
    private val createUser: CreateUser,
    private val getUser: GetUser,
) : UserService { /* … */ }
```

A dependency the graph supplies, a setting fixed for every deployment kept private, and a setting that varies between deployments carried by a configuration the module assembles (`ProjectRules.injectableConstructorsHaveNoDefaults`):

```kotlin
// feature.orders.server.domain.ReconcileOrders.kt (:server)
internal class ReconcileOrdersImpl(
    private val getOrdersAwaitingReconciliation: GetOrdersAwaitingReconciliation,
    private val updateOrder: UpdateOrder,
    private val clock: Clock,
    private val config: ReconciliationConfig,
) : ReconcileOrders {
    private val batchSize = 50
    // …
}

// feature.orders.server.domain.ReconciliationConfig.kt (:server)
internal data class ReconciliationConfig(val cleanupTimeout: Duration)

// feature.orders.ordersServerDependencies.kt (:server)
val ordersServerDependencies = module {
    single { ReconciliationConfig(cleanupTimeout = 15.seconds) }
    singleOf(::ReconcileOrdersImpl).bind(ReconcileOrders::class)
}
```

The clock is bound once, by the `:app` shell: `single<Clock> { Clock.System }`. Not `clock: Clock = Clock.System` or `cleanupTimeout: Duration = 15.seconds` on the constructor: `singleOf` resolves both from the graph, the defaults never apply, and an unbound `Duration` fails at the first resolution of `ReconcileOrdersImpl`. Not `single { ReconcileOrdersImpl(get(), get(), get(), cleanupTimeout = 15.seconds) }`: the setting is fixed at the binding site, and Koin no longer validates the constructor (`ProjectRules.constructorReferenceBindings`).
