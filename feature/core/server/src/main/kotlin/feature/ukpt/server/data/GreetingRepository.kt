package feature.ukpt.server.data

import feature.ukpt.server.data.storage.GreetingInsert
import feature.ukpt.server.data.storage.GreetingStorage
import feature.ukpt.server.domain.FlowOfGreetingHistory
import feature.ukpt.server.domain.UpdateGreetingHistory
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

internal class GreetingRepository(
    private val greetingStorage: GreetingStorage,
) {

    val flowOfGreetingHistory = FlowOfGreetingHistory {
        greetingStorage.observe().map { rows -> rows.map { it.toDomain() } }
    }

    val updateGreetingHistory = UpdateGreetingHistory { update ->
        when (update) {
            is UpdateGreetingHistory.Update.Add -> greetingStorage.insert(GreetingInsert(text = update.text))
            is UpdateGreetingHistory.Update.Remove -> Uuid.parseOrNull(update.id)?.let { greetingStorage.delete(it) }
            UpdateGreetingHistory.Update.Reset -> greetingStorage.deleteAll()
        }
    }
}
