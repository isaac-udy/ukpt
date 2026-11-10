package platform.testing.robots

internal const val ROBOT_TIMEOUT_MILLIS = 10_000L

/**
 * Opens [start] in [driver]'s tab, then drives it as a user does, one page at a time. [block]
 * suspends so a test can run scenarios between page visits.
 */
suspend fun asUser(driver: PageDriver, start: String, block: suspend User.() -> Unit) {
    driver.open(start)
    User(driver).block()
}

class User internal constructor(private val driver: PageDriver) {

    /**
     * Waits until the tab shows one of [paths], then runs [block] on that page's robot. A page
     * re-rendered by a form post without scripts shows at the post's path, so a robot lists it too.
     */
    fun <R : PageRobot> on(paths: Set<String>, robot: (PageDriver) -> R, block: R.() -> Unit) {
        val deadline = System.nanoTime() + ROBOT_TIMEOUT_MILLIS * 1_000_000
        while (driver.path !in paths) {
            if (System.nanoTime() > deadline) throw AssertionError("Expected the page at ${paths.joinToString(" or ")}; the tab shows ${driver.path}")
            Thread.sleep(20)
        }
        robot(driver).block()
    }

    fun <R : PageRobot> on(path: String, robot: (PageDriver) -> R, block: R.() -> Unit) = on(setOf(path), robot, block)
}
