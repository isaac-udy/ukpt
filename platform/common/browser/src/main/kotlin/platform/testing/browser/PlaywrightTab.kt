package platform.testing.browser

import com.microsoft.playwright.ConsoleMessage
import com.microsoft.playwright.Page
import platform.server.web.APP_ERROR_ID
import platform.testing.robots.Locator
import platform.testing.robots.PageDriver
import java.net.URI
import com.microsoft.playwright.Locator as PlaywrightLocator

/** A Chromium tab, as the page robots drive it. Dialogs such as `hx-confirm` are accepted. */
internal class PlaywrightTab(private val page: Page, private val baseUrl: String) : PageDriver {

    private val consoleErrors = mutableListOf<String>()

    init {
        page.onConsoleMessage { message: ConsoleMessage -> if (message.type() == "error") consoleErrors += message.text() }
        page.onPageError { error -> consoleErrors += error }
        page.onDialog { dialog -> dialog.accept() }
    }

    override val path: String get() = URI(page.url()).path.ifEmpty { "/" }

    override fun open(path: String) {
        page.navigate(baseUrl + path)
    }

    override fun texts(locator: Locator): List<String> =
        resolve(locator).allTextContents().map { it.replace(WHITESPACE, " ").trim() }

    override fun values(locator: Locator): List<String> = resolve(locator).all().map { it.inputValue() }

    override fun click(locator: Locator) = resolve(locator).click()

    override fun fill(locator: Locator, text: String) = resolve(locator).fill(text)

    override fun selectOption(locator: Locator, value: String) {
        resolve(locator).selectOption(value)
    }

    override fun setChecked(locator: Locator, checked: Boolean) = resolve(locator).setChecked(checked)

    override fun errorText(): String? =
        page.locator("#$APP_ERROR_ID:not([hidden])").allTextContents().firstOrNull()?.trim()?.ifEmpty { null }

    fun assertNoConsoleErrors() {
        if (consoleErrors.isNotEmpty()) throw AssertionError("The page logged errors to the console:\n" + consoleErrors.joinToString("\n"))
    }

    private fun resolve(locator: Locator): PlaywrightLocator {
        val first = locator.steps.first()
        var resolved = page.locator(first.css, Page.LocatorOptions().apply { first.hasText?.let(::setHasText) })
        locator.steps.drop(1).forEach { step ->
            resolved = resolved.locator(step.css, PlaywrightLocator.LocatorOptions().apply { step.hasText?.let(::setHasText) })
        }
        return resolved
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
