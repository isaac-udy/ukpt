// The test category annotations, one of which every test class has (see the Testing architecture
// rules). On the JVM each annotation also applies a JUnit 5 tag and timeout, so a category runs on
// its own (`./gradlew unitTest`); other targets select tests by task.
plugins {
    id("ukpt.kmp-library")
    id("ukpt.html-web")
}

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "platform.testing"
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.udytils.architectureAnnotations)
        }
        jvmMain.dependencies {
            api(project.dependencies.platform(libs.junit.bom))
            api(libs.junit.jupiterApi)
        }
    }
}
