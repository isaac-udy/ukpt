package platform.testing.robots

/**
 * Elements on a page: a CSS selector, optionally narrowed to elements whose text contains
 * [hasText], then searched within by each later step. Every [PageDriver] reads the same locator.
 */
class Locator private constructor(val steps: List<Step>) {

    class Step(val css: String, val hasText: String?)

    /** The elements matching [css] inside this locator's elements. */
    fun find(css: String, hasText: String? = null): Locator = Locator(steps + Step(css, hasText))

    override fun toString(): String = steps.joinToString(" >> ") { step ->
        step.hasText?.let { "${step.css}:has-text(\"$it\")" } ?: step.css
    }

    companion object {
        fun css(css: String, hasText: String? = null): Locator = Locator(listOf(Step(css, hasText)))
    }
}
