/**
 * Convention plugin for the module that holds End To End Tests, `:app:server`.
 *
 * Configures: `installEndToEndBrowser`, which installs the Chromium headless shell Playwright drives
 * (with its system libraries when `CI` is set), and makes `test` and `endToEndTest` depend on it. Playwright
 * never downloads browsers itself: unasked, it fetches three.
 *
 * The module's build file declares `:platform:common:browser` and the features' `:tests` modules.
 */
plugins {
    id("ukpt.jvm-base")
}

val testRuntimeClasspath = configurations.named("testRuntimeClasspath")
val onCi = providers.environmentVariable("CI").isPresent

val installEndToEndBrowser = tasks.register<JavaExec>("installEndToEndBrowser") {
    group = "verification"
    description = "Installs the Chromium headless shell that the End To End Tests drive."
    classpath = files(testRuntimeClasspath)
    mainClass.set("com.microsoft.playwright.CLI")
    args(listOfNotNull("install", "--only-shell", if (onCi) "--with-deps" else null, "chromium"))
}

tasks.withType<Test>().matching { it.name in setOf("test", "endToEndTest") }.configureEach {
    dependsOn(installEndToEndBrowser)
}

tasks.withType<Test>().configureEach {
    environment("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")
}
