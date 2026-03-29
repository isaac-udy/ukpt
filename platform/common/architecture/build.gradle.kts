plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.konsist)
}

tasks.withType<Test> {
    outputs.upToDateWhen { false }
}
