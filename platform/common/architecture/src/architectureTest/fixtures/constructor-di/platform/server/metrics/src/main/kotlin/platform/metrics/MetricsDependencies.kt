package platform.metrics

import org.koin.core.module.Module
import org.koin.dsl.module

val metricsDependencies: Module = module {
    single { MetricsClient(get()) }
}
