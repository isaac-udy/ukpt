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
    // Applied by ukpt.snapshot-testing-base, so it must be on the convention plugins' classpath.
    implementation(libs.paparazzi.gradlePlugin)
    // Not applied here — `:app:server` applies it — but ukpt.server-packaging configures the
    // `shadowJar` task the Ktor plugin brings, so both have to be compilable. Shadow is named
    // separately because Ktor depends on it at runtime only, which leaves it off the compile
    // classpath (see the catalog for keeping the two versions equal).
    implementation(libs.ktor.gradlePlugin)
    implementation(libs.shadow.gradlePlugin)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}
