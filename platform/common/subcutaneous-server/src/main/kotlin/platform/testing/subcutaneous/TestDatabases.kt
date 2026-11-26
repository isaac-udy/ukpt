package platform.testing.subcutaneous

import dev.isaacudy.udytils.postgres.PostgresConfig
import dev.isaacudy.udytils.postgres.embedded.ClonedDatabase
import dev.isaacudy.udytils.postgres.embedded.EmbeddedPostgresLifecycle
import dev.isaacudy.udytils.postgres.embedded.MigratedTemplate
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * One embedded Postgres per test JVM, migrated once; each test runs against its own clone.
 * Background threads clone the next tests' databases and drop each closed one while tests run.
 */
internal object TestDatabases {
    // Two clones in flight keep up with a test that takes less time than one clone.
    private const val READY = 2

    // Clones outpace the single drop thread; without a bound, hundreds of clones fill the disk.
    private const val PENDING_DROPS = 4

    private val postgres by lazy {
        EmbeddedPostgresLifecycle().also { postgres ->
            Runtime.getRuntime().addShutdownHook(Thread(postgres::close))
        }
    }

    private val template by lazy { MigratedTemplate(postgres, name = "subcutaneous_template") }

    private val cloner = daemonThreads("subcutaneous-clone", READY)
    private val dropper = daemonThreads("subcutaneous-drop", 1)
    private val ready = ArrayDeque<Future<ClonedDatabase>>()
    private val drops = ArrayDeque<Future<*>>()

    @Synchronized
    fun clone(): TestDatabase {
        while (drops.size > PENDING_DROPS) await(drops.removeFirst())
        while (ready.size <= READY) ready.addLast(cloner.submit<ClonedDatabase> { template.clone() })
        val database = await(ready.removeFirst())
        return TestDatabase(database.config) { queueDrop(database) }
    }

    @Synchronized
    private fun queueDrop(database: ClonedDatabase) {
        drops.addLast(dropper.submit(database::close))
    }

    private fun <T> await(future: Future<T>): T = try {
        future.get()
    } catch (failure: ExecutionException) {
        throw failure.cause ?: failure
    }

    private fun daemonThreads(name: String, count: Int): ExecutorService = Executors.newFixedThreadPool(count) { task ->
        Thread(task, name).apply { isDaemon = true }
    }
}

/** A test's database; [close] queues the drop and returns. */
internal class TestDatabase(val config: PostgresConfig, private val drop: () -> Unit) : AutoCloseable {
    override fun close() = drop()
}
