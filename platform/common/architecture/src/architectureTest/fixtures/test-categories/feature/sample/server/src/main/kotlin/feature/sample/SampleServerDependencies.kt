package feature.sample

import feature.sample.server.data.SampleRepository
import feature.sample.server.domain.AddSample
import feature.sample.server.domain.AddSampleImpl
import feature.sample.server.domain.FlowOfSamples
import feature.sample.server.domain.SaveSample
import feature.sample.server.web.SampleRoutes
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import platform.server.web.WebRoutes

val sampleServerDependencies = module {
    singleOf(::SampleRepository)
    single<FlowOfSamples> { get<SampleRepository>().flowOfSamples }
    single<SaveSample> { get<SampleRepository>().saveSample }

    singleOf(::AddSampleImpl) bind AddSample::class

    singleOf(::SampleRoutes) bind WebRoutes::class
}
