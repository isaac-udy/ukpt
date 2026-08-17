package platform.server.postgres

/**
 * Runs a block of work inside one database transaction.
 *
 * **Joining, not nesting.** [inTransaction] opens a transaction when none is ambient and joins the
 * ambient one when there is. The outermost call owns the commit, so every write in the block —
 * including those made by nested calls and by the storage classes it calls — commits or rolls back
 * together. There are no savepoints: an inner failure cannot be caught and the outer work kept.
 *
 * **One process, one database.** The guarantee is a single Postgres transaction on a single
 * connection in this JVM; another process, another database, or a background job started inside the
 * block observes the writes only after the outermost transaction commits.
 *
 * **No integration calls inside the block.** A block holds a pooled connection for as long as it
 * runs, so a network call inside it starves the pool for a round trip and can hold row locks across
 * it. Do the network work first, then open the transaction with the result in hand.
 */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}
