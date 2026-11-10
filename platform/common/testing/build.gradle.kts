// The test category annotations every test class carries (see the Testing architecture rules).
// Each one carries a JUnit 5 tag and timeout, so a category runs on its own (`./gradlew unitTest`).
plugins {
    id("ukpt.jvm-library")
}

dependencies {
    api(libs.udytils.architectureAnnotations)
    api(project.dependencies.platform(libs.junit.bom))
    api(libs.junit.jupiterApi)
}
