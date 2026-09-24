plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.gradlePlugin)
    // ukpt.server-packaging types the `shadowJar` task, so both must be on this classpath. Shadow
    // is named separately: Ktor depends on it at runtime only, so it never arrives transitively.
    implementation(libs.ktor.gradlePlugin)
    implementation(libs.shadow.gradlePlugin)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}
