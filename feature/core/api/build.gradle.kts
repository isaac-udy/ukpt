plugins {
    id("ukpt.jvm-library")
}

dependencies {
    api(libs.kotlinx.coroutinesCore)
    api(libs.kotlinx.serialization)
    api(libs.kotlinx.datetime)
    api(libs.udytils.core)

    testImplementation(projects.platform.common.testing)
    testImplementation(libs.kotlin.test)
}
