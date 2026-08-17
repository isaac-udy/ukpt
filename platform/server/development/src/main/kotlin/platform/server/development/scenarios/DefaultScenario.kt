package platform.server.development.scenarios

import dev.isaacudy.udytils.postgres.embedded.DevScenario
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * What a brand-new dev database starts out holding. Empty deliberately: the template has no schema
 * yet, and this object is the seam a project fills in as it grows its first tables.
 *
 * A scenario runs exactly once, against a freshly-created cluster, so it may assume an empty schema.
 */
object DefaultScenario : DevScenario {
    override val name: String = "default"
    override val description: String = "The migrated schema, with no seed rows yet."

    override suspend fun apply(database: Database) {
    }
}
