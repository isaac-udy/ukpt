package platform.testing.robots

/**
 * One page's user actions (`click…`, `enter…`) and what the user sees (`see…`). Only robots locate
 * elements. Each step waits up to [ROBOT_TIMEOUT_MILLIS] for the page to reach the state it needs.
 */
abstract class PageRobot(private val driver: PageDriver) {

    protected fun click(locator: Locator) {
        awaitOne(locator)
        driver.click(locator)
    }

    protected fun enter(locator: Locator, text: String) {
        awaitOne(locator)
        driver.fill(locator, text)
    }

    protected fun clear(locator: Locator) = enter(locator, "")

    protected fun select(locator: Locator, value: String) {
        awaitOne(locator)
        driver.selectOption(locator, value)
    }

    protected fun toggle(locator: Locator, checked: Boolean) {
        awaitOne(locator)
        driver.setChecked(locator, checked)
    }

    /** Waits until exactly one element matches [locator] and its text is [text]. */
    protected fun seeText(locator: Locator, text: String) =
        await("Expected $locator to show \"$text\"", { driver.texts(locator) }) { it == listOf(text) }

    /** Waits until the elements [locator] matches show [texts], in order. */
    protected fun seeTexts(locator: Locator, texts: List<String>) =
        await("Expected $locator to show $texts", { driver.texts(locator) }) { it == texts }

    /** Waits until exactly one element matches [locator] and its value is [value]. */
    protected fun seeValue(locator: Locator, value: String) =
        await("Expected $locator to hold \"$value\"", { driver.values(locator) }) { it == listOf(value) }

    /** Waits until no element matches [locator]. */
    protected fun seeNone(locator: Locator) =
        await("Expected nothing to match $locator", { driver.texts(locator) }) { it.isEmpty() }

    private fun awaitOne(locator: Locator) =
        await("Expected exactly one element to match $locator", { driver.texts(locator) }) { it.size == 1 }

    private fun await(expectation: String, read: () -> List<String>, matches: (List<String>) -> Boolean) {
        val deadline = System.nanoTime() + ROBOT_TIMEOUT_MILLIS * 1_000_000
        while (true) {
            val shown = read()
            if (matches(shown)) return
            if (System.nanoTime() > deadline) throw AssertionError("$expectation; ${describe(shown)}")
            Thread.sleep(POLL_MILLIS)
        }
    }

    private fun describe(shown: List<String>): String = listOfNotNull(
        if (shown.isEmpty()) "nothing matches" else "found ${shown.joinToString { "\"$it\"" }}",
        "on ${driver.path}",
        driver.errorText()?.let { "the page shows the error \"$it\"" },
    ).joinToString(", ")
}

private const val POLL_MILLIS = 20L
