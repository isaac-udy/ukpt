package platform.server.development

import dev.isaacudy.udytils.postgres.embedded.DevServerConfig
import dev.isaacudy.udytils.postgres.embedded.DevServerStorage
import platform.server.development.scenarios.DefaultScenario
import java.nio.file.Path

/**
 * Turns an application's dev-database switches into a [DevServerConfig].
 *
 * The application owns the env vars — this module only knows the *values* they can carry, so a
 * project can rename or re-source them (a Gradle property, a CLI flag, a config file) without
 * touching the dev-database wiring.
 */
object UkptDevDatabase {

    /** A cluster under the given base directory, on a fixed port: the same data after a restart. */
    const val MODE_EMBEDDED: String = "embedded"

    /** A temp cluster on a random port, discarded on shutdown. */
    const val MODE_EPHEMERAL: String = "ephemeral"

    /**
     * The dev-server configuration [mode] asks for, or `null` when [mode] names no dev database —
     * which is the signal to connect to a real Postgres instead.
     *
     * [scenarioName] is an explicit request and is validated eagerly ([DevScenarios.byName]); a
     * fresh cluster with no request gets [DefaultScenario].
     */
    fun configFor(mode: String?, scenarioName: String?, baseDirectory: Path): DevServerConfig? {
        val storage = when (mode) {
            MODE_EMBEDDED -> DevServerStorage.Persistent(baseDirectory = baseDirectory)
            MODE_EPHEMERAL -> DevServerStorage.Ephemeral
            else -> return null
        }
        return DevServerConfig(
            storage = storage,
            freshScenario = DefaultScenario,
            requestedScenario = scenarioName?.takeIf { it.isNotBlank() }?.let(DevScenarios::byName),
        )
    }
}
