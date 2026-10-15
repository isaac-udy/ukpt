import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

// The Compose HTML web client. settings.gradle.kts includes this module only when
// `ukpt.htmlWebClient` is true. It has no Compose UI dependency, and verifyHtmlBundle fails the
// build if Compose UI reaches the production bundle.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinKsp)
}

kotlin {
    js {
        outputModuleName.set("App")
        // A local, not project.projectDir inside the block: the webpack config is stored in the
        // configuration cache, and reading the Project there captures it.
        val devServerRoot = layout.projectDirectory.asFile.path
        browser {
            commonWebpackConfig {
                outputFileName = "App.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static(devServerRoot)
                }
            }
        }
        binaries.executable()
    }

    compilerOptions {
        // Match the KMP convention plugins so this module can consume the feature clients, whose
        // output is marked pre-release by these preview features.
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xexplicit-backing-fields",
            "-Xexpect-actual-classes",
        )
    }

    sourceSets {
        jsMain.dependencies {
            implementation(projects.feature.core.client)

            implementation(libs.compose.htmlCore)
            implementation(libs.enro.html)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.koin.core)
            implementation(libs.ktor.clientJs)
            implementation(libs.kotlinx.browser)
        }
    }
}

val htmlStylesheets = configurations.dependencyScope("htmlStylesheets")
val htmlStylesheetFiles = configurations.resolvable("htmlStylesheetFiles") {
    extendsFrom(htmlStylesheets.get())
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("ukpt-html-stylesheet"))
    }
}

dependencies {
    add("kspJs", libs.enro.processor)
    add(htmlStylesheets.name, projects.platform.client.designHtml)
}

tasks.named<ProcessResources>("jsProcessResources") {
    from(htmlStylesheetFiles)
}

val verifyHtmlBundle = tasks.register("verifyHtmlBundle") {
    description = "Fails if skiko or Compose UI is in the production bundle."
    group = "verification"
    val distribution = tasks.named("jsBrowserDistribution")
    dependsOn(distribution)
    val distributionDirectory = layout.buildDirectory.dir("dist/js/productionExecutable")
    doLast {
        val composeUiFiles = distributionDirectory.get().asFile.walkTopDown()
            .filter { it.isFile }
            .filter { file -> file.name.contains("skiko") || file.readText().contains("skiko") }
            .toList()
        check(composeUiFiles.isEmpty()) {
            "Compose UI is in the Compose HTML bundle: ${composeUiFiles.joinToString { it.name }}. " +
                "Add a @NavigationDestination.PlatformOverride Compose HTML screen for each Compose UI " +
                "screen this client reaches, and remove Compose UI dependencies from :app:client:web-html."
        }
    }
}

tasks.named("check") {
    dependsOn(verifyHtmlBundle)
}
