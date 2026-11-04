package platform.testing.subcutaneous

import dev.isaacudy.udytils.postgres.embedded.ClonedDatabase
import dev.isaacudy.udytils.postgres.embedded.EmbeddedPostgresLifecycle
import dev.isaacudy.udytils.postgres.embedded.MigratedTemplate

/** One embedded Postgres per test JVM, migrated once; each test runs against its own clone. */
internal object TestDatabases {
    private val postgres by lazy {
        EmbeddedPostgresLifecycle().also { postgres ->
            Runtime.getRuntime().addShutdownHook(Thread(postgres::close))
        }
    }

    private val template by lazy { MigratedTemplate(postgres, name = "subcutaneous_template") }

    fun clone(): ClonedDatabase = template.clone()
}
