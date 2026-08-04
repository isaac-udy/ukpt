package platform.server.development.scenarios

import dev.isaacudy.udytils.postgres.embedded.DevScenario
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * What a brand-new dev database starts out holding.
 *
 * The template has no schema yet, so there is nothing to insert — this object exists to be the
 * seam a project fills in as it grows its first tables. Seeds are written against the generated
 * Exposed tables in `platform.server.postgres.tables`, in one `suspendTransaction`, with fixed
 * ids so a URL that worked yesterday still works today:
 *
 * ```kotlin
 * override suspend fun apply(database: Database) {
 *     suspendTransaction(db = database) {
 *         WidgetsTable.insert { it.setFromRow(WidgetRow(id = fixedWidgetId, name = "Example")) }
 *     }
 * }
 * ```
 *
 * A scenario runs exactly once, against a freshly-created cluster, so it may assume an empty
 * schema — see `DevServer`'s seed-once semantics.
 */
object DefaultScenario : DevScenario {
    override val name: String = "default"
    override val description: String = "The migrated schema, with no seed rows yet."

    override suspend fun apply(database: Database) {
    }
}
