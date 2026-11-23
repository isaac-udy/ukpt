import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.js.testing.karma.KotlinKarma

/**
 * Sets the Mocha timeout of the module's Karma test tasks (`jsBrowserTest`, `wasmJsBrowserTest`)
 * to 70 s. Mocha's 2 s default fails a Subcutaneous Test that waits longer; the harness enforces
 * its own 60 s limit.
 *
 * Applied by: ukpt.feature-tests and `:platform:common:subcutaneous`.
 */
val karmaConfigDirectory = layout.buildDirectory.dir("subcutaneous-karma")

val writeKarmaConfig = tasks.register("writeSubcutaneousKarmaConfig") {
    val directory = karmaConfigDirectory
    outputs.dir(directory)
    doLast {
        directory.get().file("mocha-timeout.js").asFile.writeText(
            "config.client.mocha = Object.assign(config.client.mocha || {}, { timeout: 70000 });\n",
        )
    }
}

tasks.withType<KotlinJsTest>().configureEach {
    dependsOn(writeKarmaConfig)
    val directory = karmaConfigDirectory.get().asFile
    onTestFrameworkSet { (this as? KotlinKarma)?.useConfigDirectory(directory) }
}
