// The Subcutaneous Test harness: a feature's production client Koin graph, its ViewModels opened
// with test navigation handles, and the edges a test replaces. `:feature:[name]:tests` modules
// depend on it. A project with a server of its own also uses `:platform:common:subcutaneous-server`,
// which runs that server in-process for the client graph to call.
plugins {
    id("ukpt.jvm-base")
}

dependencies {
    api(projects.platform.common.testing)
    api(libs.koin.core)
    api(libs.enro.test)
    api(libs.udytils.viewmodel)
    api(libs.kotlinx.coroutinesCore)

    implementation(libs.kotlinx.coroutinesTest)
    implementation(libs.androidx.lifecycle.viewmodel)
}
