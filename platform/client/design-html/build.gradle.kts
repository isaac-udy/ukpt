import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The design system for the Compose HTML web client: the primitives in jsMain and the stylesheet
// in src/css. The jvm target exists only for UkptDesignCssTest, which checks the stylesheet's
// tokens against :platform:client:design.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("ukpt.html-web")
}

val stylesheet = layout.projectDirectory.file("src/css/ukpt-design.css")

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        jvmTest.dependencies {
            implementation(projects.platform.client.design)
            implementation(libs.kotlin.test)
        }
        jsMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.htmlCore)
            implementation(libs.udytils.uiHtml)
        }
    }
}

// Kotlin/JS doesn't copy a library's resources into the app's distribution, so
// :app:client:web-html resolves the stylesheet through this configuration.
configurations.consumable("htmlStylesheetElements") {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("ukpt-html-stylesheet"))
    }
    outgoing.artifact(stylesheet)
}

tasks.named<Test>("jvmTest") {
    inputs.file(stylesheet).withPropertyName("stylesheet").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("ukpt.designStylesheet", stylesheet.asFile.relativeTo(projectDir).path)
}
