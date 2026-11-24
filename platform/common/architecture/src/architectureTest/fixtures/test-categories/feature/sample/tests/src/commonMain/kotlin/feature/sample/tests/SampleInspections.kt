package feature.sample.tests

import org.jetbrains.exposed.v1.jdbc.selectAll
import platform.server.postgres.tables.SamplesTable
import platform.testing.subcutaneous.ClientInspection
import platform.testing.subcutaneous.ServerInspection
import platform.testing.subcutaneous.database

fun ClientInspection.recordedSamples(): List<String> = client<FakeSampleSource>().requests.calls

suspend fun ServerInspection.storedLabels(): List<String> = database {
    SamplesTable.selectAll().map { it[SamplesTable.label] }
}
