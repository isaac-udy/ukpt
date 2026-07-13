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
        // Match the KMP convention plugins so this module can consume :app:client:common,
        // whose output is marked pre-release by these preview features.
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xexplicit-backing-fields",
            "-Xexpect-actual-classes",
        )
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(projects.app.client.common)

            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(libs.ktor.clientJs)
            implementation(libs.kotlinx.browser)
        }
    }
}

// macOS Finder/Spotlight drops `.DS_Store` files into the Kotlin/Wasm incremental-compilation
// cache directory; the IC scanner then treats each as a (now-missing) library and crashes with
// "IC internal error: can not find removed library name". Purge them from this module's klib
// cache before any wasm task runs, so incremental dev builds stay reliable on macOS.
tasks.matching { it.name.contains("WasmJs", ignoreCase = true) }.configureEach {
    // `klibDir` is deliberately a local of this configuration action, not a script-level `val`, and
    // the `doFirst` action below closes over only it. A `doFirst` lambda that instead referenced
    // `layout` (i.e. `project.layout`) or a script-level property would drag the `Project` / build
    // script into the task's serialized state, which the configuration cache rejects — for every
    // task this filter matches. Keeping the capture to a `Provider` keeps the cache working.
    val klibDir = layout.buildDirectory.dir("klib")
    doFirst {
        val klib = klibDir.get().asFile
        if (klib.exists()) {
            klib.walkTopDown().filter { it.name == ".DS_Store" }.forEach { it.delete() }
        }
    }
}
