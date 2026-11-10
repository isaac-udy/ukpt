package platform.server.development.scenarios

import dev.isaacudy.udytils.postgres.embedded.DevScenario
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * What a brand-new dev database starts out holding: the migrated schema and no rows, so the greeting
 * screen starts empty.
 *
 * A scenario runs exactly once, against a freshly-created cluster, so it may assume an empty schema.
 */
object DefaultScenario : DevScenario {
    override val name: String = "default"
    override val description: String = "The migrated schema, with no seed rows."

    override suspend fun apply(database: Database) {
    }
}
