package platform.metrics

import org.koin.core.module.Module
import org.koin.dsl.module
import vendor.http.VendorHttpClient

val metricsDependencies: Module = module {
    single { MetricsClient(get()) }
    single { VendorHttpClient(get()) }
}
