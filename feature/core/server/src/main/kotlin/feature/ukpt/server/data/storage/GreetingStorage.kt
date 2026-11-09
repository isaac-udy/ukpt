package feature.ukpt.server.data.storage

import dev.isaacudy.udytils.postgres.PgNotificationBus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import platform.server.postgres.tables.GreetingRow
import platform.server.postgres.tables.GreetingsTable
import kotlin.uuid.Uuid

internal class GreetingStorage(
    private val database: Database,
    private val notificationBus: PgNotificationBus,
) {

    suspend fun list(): List<GreetingRow> = suspendTransaction(db = database) {
        GreetingsTable.selectAll()
            .orderBy(GreetingsTable.createdAt to SortOrder.ASC, GreetingsTable.id to SortOrder.ASC)
            .map(::GreetingRow)
    }

    // The initial read runs after LISTEN is active and the subscription is live, so a change
    // between the read and the first notification is not lost.
    fun observe(): Flow<List<GreetingRow>> = flow {
        notificationBus.awaitListening(CHANNEL)
        emitAll(notificationBus.listen(CHANNEL).onSubscription { emit(INITIAL_READ) })
    }
        .conflate()
        .map { list() }
        .distinctUntilChanged()

    suspend fun insert(insert: GreetingInsert) {
        suspendTransaction(db = database) {
            GreetingsTable.insert {
                it[id] = Uuid.random()
                it[text] = insert.text
            }
        }
    }

    suspend fun deleteAll() {
        suspendTransaction(db = database) {
            GreetingsTable.deleteAll()
        }
    }

    companion object {
        const val CHANNEL = "greeting_changed"
        private const val INITIAL_READ = ""
    }
}
