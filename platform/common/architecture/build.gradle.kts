plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    testImplementation(libs.kotlin.testJunit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.konsist)
    testImplementation(libs.kotlin.reflect)
}

tasks.withType<Test> {
    useJUnitPlatform()
    outputs.upToDateWhen { false }
}
