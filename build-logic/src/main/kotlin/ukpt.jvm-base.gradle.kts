import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import ukpt.ArchiveNaming

/**
 * Base convention for every plain Kotlin/JVM module in the project.
 *
 * Applies: KotlinJvm
 * Configures: archive naming, Java/Kotlin bytecode target, shared Kotlin compiler options, and the
 * test category tasks (ukpt.test-categories).
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("ukpt.test-categories")
}

base {
    // Artifact names must be unique across the whole build: `installDist` copies every module's jar
    // into one `lib/`, and Gradle's default name is the leaf directory, which features repeat.
    archivesName.set(ArchiveNaming.baseNameFor(project.path))
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
