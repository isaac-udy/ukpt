import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("App")
        browser {
            commonWebpackConfig {
                outputFileName = "App.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static(project.projectDir.path)
                }
            }
        }
        binaries.executable()
    }

    compilerOptions {
        // Match the KMP convention plugins so this module can consume :app:client:shared,
        // whose output is marked pre-release by these preview features.
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xexplicit-backing-fields",
            "-Xexpect-actual-classes",
        )
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(projects.app.client.shared)

            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(libs.ktor.clientJs)
            implementation(libs.kotlinx.browser)
        }
    }
}
