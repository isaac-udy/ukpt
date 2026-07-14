import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Base convention for every plain Kotlin/JVM module in the project.
 *
 * Applies: KotlinJvm
 * Configures: Java/Kotlin bytecode target and shared Kotlin compiler options.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
        )
    }
}
