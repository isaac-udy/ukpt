package platform.testing.robots

/**
 * One browser tab, as a robot drives it. The Subcutaneous harness implements it over the server's
 * HTML; End To End Tests implement it over a real browser. Every call acts on the page as it is
 * now; [PageRobot] does the waiting.
 */
interface PageDriver {
    /** The path of the page the tab shows, without the query string. */
    val path: String

    fun open(path: String)

    /** The whitespace-normalised text of every element [locator] matches, in document order. */
    fun texts(locator: Locator): List<String>

    /** The value attribute of every element [locator] matches, in document order. */
    fun values(locator: Locator): List<String>

    fun click(locator: Locator)

    fun fill(locator: Locator, text: String)

    fun selectOption(locator: Locator, value: String)

    fun setChecked(locator: Locator, checked: Boolean)

    /** What the page shows a user about the last failed request, if anything; for failure messages. */
    fun errorText(): String?
}
