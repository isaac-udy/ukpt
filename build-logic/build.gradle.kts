plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.gradlePlugin)
    implementation(libs.android.gradlePlugin)
    implementation(libs.compose.gradlePlugin)
    implementation(libs.compose.compiler.gradlePlugin)
    // Applied by ukpt.snapshot-testing, so it must be on the convention plugins' classpath.
    implementation(libs.paparazzi.gradlePlugin)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}
