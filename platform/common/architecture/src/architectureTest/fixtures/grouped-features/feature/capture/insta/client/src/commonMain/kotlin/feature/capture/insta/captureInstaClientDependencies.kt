package feature.capture.insta

import feature.capture.insta.client.data.InstaFileStore
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val captureInstaClientDependencies = module {
    singleOf(::InstaFileStore)
}
