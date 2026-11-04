package feature.sample.tests

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import platform.testing.subcutaneous.Scenario

suspend fun Scenario.rawSamples() {
    transaction { exec("INSERT INTO samples (label) VALUES ('a')") }
}
