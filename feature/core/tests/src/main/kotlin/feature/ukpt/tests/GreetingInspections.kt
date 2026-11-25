package feature.ukpt.tests

import feature.ukpt.Greeting
import feature.ukpt.server.domain.FlowOfGreetingHistory
import kotlinx.coroutines.flow.first
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.selectAll
import platform.server.postgres.tables.GreetingsTable
import platform.testing.subcutaneous.ServerInspection
import platform.testing.subcutaneous.database

/** The greeting history the server domain returns. */
suspend fun ServerInspection.currentGreetingHistory(): List<Greeting> = server<FlowOfGreetingHistory>()().first()

/** The text of each greeting row in the database, oldest first. */
suspend fun ServerInspection.storedGreetingTexts(): List<String> = database {
    GreetingsTable.selectAll()
        .orderBy(GreetingsTable.createdAt to SortOrder.ASC, GreetingsTable.id to SortOrder.ASC)
        .map { it[GreetingsTable.text] }
}
