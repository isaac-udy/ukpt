package platform.testing.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import platform.testing.robots.User
import platform.testing.robots.asUser
import platform.testing.subcutaneous.InProcessServerScope

/**
 * Opens [start] in a new headless Chromium tab with its own cookies, against the running server,
 * then runs [block] as the user of that tab. Fails afterwards if the page logged an error to the
 * console, which is where the browser reports a content security policy violation.
 *
 * [block] runs on the calling thread; Playwright's objects are bound to the thread that created
 * them.
 */
suspend fun InProcessServerScope.asUserInBrowser(start: String, block: suspend User.() -> Unit) {
    val context = Browsers.chromium.newContext(Browser.NewContextOptions().setBaseURL(baseUrl))
    try {
        context.setDefaultTimeout(BROWSER_TIMEOUT_MILLIS)
        val tab = PlaywrightTab(context.newPage(), baseUrl)
        asUser(tab, start, block)
        tab.assertNoConsoleErrors()
    } finally {
        context.close()
    }
}

private const val BROWSER_TIMEOUT_MILLIS = 10_000.0

/** One Playwright and one Chromium per test JVM; each test gets its own browser context. */
private object Browsers {
    private val playwright by lazy {
        Playwright.create().also { playwright -> Runtime.getRuntime().addShutdownHook(Thread(playwright::close)) }
    }

    val chromium: Browser by lazy {
        try {
            playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(true))
        } catch (failure: RuntimeException) {
            throw IllegalStateException("Chromium did not start. Run `./gradlew installEndToEndBrowser`.", failure)
        }
    }
}
