package platform.testing.subcutaneous

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

/**
 * Runs [block] in a read-only transaction on the test's database, for a stored fact the server
 * domain does not expose. Postgres rejects a write in the transaction.
 */
suspend fun <T> ServerInspection.database(block: JdbcTransaction.() -> T): T =
    suspendTransaction(db = server<Database>()) {
        // suspendTransaction(readOnly = true) leaves the Postgres transaction writable.
        exec("SET TRANSACTION READ ONLY")
        block()
    }
