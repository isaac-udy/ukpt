package platform.server.development

import dev.isaacudy.udytils.postgres.embedded.DevScenario
import dev.isaacudy.udytils.postgres.embedded.EmptyScenario
import platform.server.development.scenarios.DefaultScenario

/**
 * Every dev scenario this project ships. Add new ones here — this list is what an operator can
 * name, and what a misspelled name is reported against.
 */
object DevScenarios {

    val all: List<DevScenario> = listOf(
        EmptyScenario,
        DefaultScenario,
    )

    /**
     * @throws IllegalArgumentException when no scenario is named [name] — an operator asking for
     * a starting state that doesn't exist wants to be told, not handed a silently different one.
     */
    fun byName(name: String): DevScenario =
        all.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException(
                "Unknown dev scenario '$name'. Known scenarios: " +
                    all.joinToString(", ") { "${it.name} (${it.description})" },
            )
}
