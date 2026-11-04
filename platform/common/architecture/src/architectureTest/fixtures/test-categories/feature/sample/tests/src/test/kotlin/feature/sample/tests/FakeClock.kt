package feature.sample.tests

import kotlin.time.Clock
import kotlin.time.Instant

object FakeClock : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(0)
}
