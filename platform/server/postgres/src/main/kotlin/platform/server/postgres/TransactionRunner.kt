package platform.server.postgres

/**
 * Runs a block of work inside one database transaction.
 *
 * **Joining, not nesting.** [inTransaction] opens a transaction when none is
 * ambient and joins the ambient one when there is. Storage calls made inside
 * [block] join the same transaction, so every write in the block commits
 * together or rolls back together, and a nested [inTransaction] widens nothing:
 * the outermost call owns the commit, and a failure anywhere discards the whole
 * block. There are no savepoints, so an inner failure cannot be caught and the
 * outer work kept.
 *
 * **One process, one database.** The guarantee is a single Postgres transaction
 * on a single connection in this JVM. It says nothing about work handed to
 * another process, another database, or a background job started inside the
 * block — those observe the writes only after the outermost transaction commits.
 *
 * **No integration calls inside the block.** A block holds a pooled connection
 * for as long as it runs, so an `IntegrationClient` call — GenAI, email,
 * transcription, object storage — starves the pool for the length of a network
 * round trip and can hold row locks across it. Do the network work first, then
 * open the transaction with the result in hand.
 */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}
