// End To End Tests' browser: the page robots driving headless Chromium through Playwright, against
// the server `inProcessServer` starts. Only `:app:server`'s tests depend on it; `ukpt.end-to-end-tests`
// installs the browser.
plugins {
    id("ukpt.jvm-base")
}

dependencies {
    api(projects.platform.common.robots)
    api(projects.platform.common.subcutaneous)

    implementation(projects.platform.server.web)
    implementation(libs.playwright)
}
