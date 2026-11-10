package feature.sample.tests

import feature.sample.server.data.SampleRepository
import kotlinx.coroutines.flow.first
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import platform.testing.subcutaneous.Scenario

suspend fun Scenario.rawSamples(): List<String> {
    transaction { exec("INSERT INTO samples (label) VALUES ('a')") }
    return server<SampleRepository>().flowOfSamples().first()
}
