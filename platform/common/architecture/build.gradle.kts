plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.konsist)
    testImplementation(libs.kotlin.reflect)
}

tasks.withType<Test> {
    outputs.upToDateWhen { false }
}
