package platform.server.development

import dev.isaacudy.udytils.postgres.embedded.DevScenario
import dev.isaacudy.udytils.postgres.embedded.EmptyScenario
import platform.server.development.scenarios.DefaultScenario

/** Every dev scenario this project ships — register new ones here; a name resolves against it. */
object DevScenarios {

    val all: List<DevScenario> = listOf(
        EmptyScenario,
        DefaultScenario,
    )

    /** @throws IllegalArgumentException when no scenario is named [name]. */
    fun byName(name: String): DevScenario =
        all.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException(
                "Unknown dev scenario '$name'. Known scenarios: " +
                    all.joinToString(", ") { "${it.name} (${it.description})" },
            )
}
