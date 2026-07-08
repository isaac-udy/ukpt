import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // AGP 9 ships built-in Kotlin support, so no `kotlin.android` plugin is applied here.
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

private val projectNamespace = providers.gradleProperty("ukpt.projectNamespace").get()

android {
    namespace = projectNamespace
    compileSdk = 36

    defaultConfig {
        applicationId = projectNamespace
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Configured on the Kotlin compile tasks so it works with AGP 9's built-in Kotlin.
// The preview-feature flags match the KMP convention plugins so this module can consume
// :app:client, whose output is marked pre-release by those features.
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xexplicit-backing-fields",
            "-Xexpect-actual-classes",
        )
    }
}

dependencies {
    implementation(projects.app.client.common)

    implementation(libs.androidx.activity.compose)
    implementation(compose.preview)
    debugImplementation(compose.uiTooling)
}
