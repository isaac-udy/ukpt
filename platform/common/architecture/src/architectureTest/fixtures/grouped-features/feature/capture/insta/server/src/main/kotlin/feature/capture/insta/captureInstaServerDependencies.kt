package feature.capture.insta

import feature.capture.insta.server.data.InstaFileStore
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val captureInstaServerDependencies = module {
    singleOf(::InstaFileStore)
}
