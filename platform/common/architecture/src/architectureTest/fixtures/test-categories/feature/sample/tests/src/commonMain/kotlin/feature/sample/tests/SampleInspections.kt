package feature.sample.tests

import feature.sample.client.domain.SampleStatus
import org.jetbrains.exposed.v1.jdbc.selectAll
import platform.server.postgres.tables.SamplesTable
import platform.testing.subcutaneous.ClientInspection
import platform.testing.subcutaneous.ServerInspection
import platform.testing.subcutaneous.database

fun ClientInspection.recordedSamples(): List<String> = client<FakeSampleSource>().requests.calls

fun ClientInspection.sampleStatus(): SampleStatus = if (recordedSamples().isEmpty()) SampleStatus.Idle else SampleStatus.Failed("recorded")

suspend fun ServerInspection.storedLabels(): List<String> = database {
    SamplesTable.selectAll().map { it[SamplesTable.label] }
}
