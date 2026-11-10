package feature.ukpt.server.data

import feature.ukpt.Greeting
import platform.server.postgres.tables.GreetingRow

internal fun GreetingRow.toDomain(): Greeting = Greeting(id = id.toString(), text = text)
