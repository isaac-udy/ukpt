/**
 * Convention plugin for modules the Compose HTML web client (:app:client:web-html) compiles
 * against. Apply it after ukpt.kmp-library or ukpt.compose-library.
 *
 * Adds the Kotlin/JS browser target and runs the module's wasmJs symbol processors for Kotlin/JS
 * too.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    js {
        browser()
    }
}

pluginManager.withPlugin("com.google.devtools.ksp") {
    configurations.named { it == "kspWasmJs" }.configureEach {
        dependencies.configureEach {
            project.dependencies.add("kspJs", copy())
        }
    }
}
