package platform.server.postgres

import dev.isaacudy.udytils.postgres.embedded.EmbeddedPostgresLifecycle
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Checked against a real (Zonky-embedded) Postgres. The table is created here rather than by a
 * migration: this module ships no schema.
 */
class TransactionRunnerTest {

    @Test
    fun `writes in one block commit together`() = runBlocking<Unit> {
        runner.inTransaction {
            insertWidget("commit-one")
            insertWidget("commit-two")
        }

        assertEquals(1, countWidgets("commit-one"))
        assertEquals(1, countWidgets("commit-two"))
    }

    @Test
    fun `writes in one block roll back together`() = runBlocking<Unit> {
        assertFailsWith<IllegalStateException> {
            runner.inTransaction {
                insertWidget("rollback-one")
                insertWidget("rollback-two")
                error("failing after both writes")
            }
        }

        assertEquals(0, countWidgets("rollback-one"))
        assertEquals(0, countWidgets("rollback-two"))
    }

    @Test
    fun `a nested block joins the outer transaction and dies with it`() = runBlocking<Unit> {
        assertFailsWith<IllegalStateException> {
            runner.inTransaction {
                insertWidget("nested-outer")
                // Completing this inner block commits nothing: the outermost frame owns the commit.
                runner.inTransaction {
                    insertWidget("nested-inner")
                }
                error("failing after the inner block returned")
            }
        }

        assertEquals(0, countWidgets("nested-outer"))
        assertEquals(0, countWidgets("nested-inner"))
    }

    companion object {
        private lateinit var embedded: EmbeddedPostgresLifecycle
        private lateinit var database: Database
        private lateinit var runner: TransactionRunner

        @BeforeClass
        @JvmStatic
        fun setUp() {
            embedded = EmbeddedPostgresLifecycle()
            database = Database.connect(
                url = embedded.config.jdbcUrl,
                user = embedded.config.username,
                password = embedded.config.password,
            )
            runner = TransactionRunner(database)
            runBlocking {
                suspendTransaction(db = database) {
                    exec("CREATE TABLE widgets (name text NOT NULL)")
                }
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            embedded.close()
        }

        private suspend fun insertWidget(name: String) = suspendTransaction(db = database) {
            exec("INSERT INTO widgets (name) VALUES ('$name')")
        }

        /** Plain JDBC, deliberately: the assertion shouldn't run through the API under test. */
        private fun countWidgets(name: String): Int =
            DriverManager.getConnection(
                embedded.config.jdbcUrl,
                embedded.config.username,
                embedded.config.password,
            ).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT count(*) FROM widgets WHERE name = '$name'").use { rows ->
                        rows.next()
                        rows.getInt(1)
                    }
                }
            }
    }
}
