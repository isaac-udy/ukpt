package platform.server.postgres

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

/** A [TransactionRunner] over Exposed's suspending transactions against [database]. */
fun TransactionRunner(database: Database): TransactionRunner = ExposedTransactionRunner(database)

/**
 * `suspendTransaction` is the joining call: with a transaction already in the
 * coroutine context it reuses that transaction and leaves the commit to the
 * outermost frame, and without one it opens a top-level transaction. Storage
 * classes issue the same call, which is what makes their writes participate in a
 * surrounding [TransactionRunner.inTransaction] block.
 */
internal class ExposedTransactionRunner(
    private val database: Database,
) : TransactionRunner {

    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        suspendTransaction(db = database) { block() }
}
