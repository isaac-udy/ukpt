package feature.sample.tests

import feature.sample.client.data.SampleTally
import feature.sample.client.domain.SampleSource
import org.jetbrains.exposed.v1.jdbc.deleteAll
import platform.server.postgres.tables.SamplesTable
import platform.testing.subcutaneous.ClientInspection
import platform.testing.subcutaneous.ServerInspection
import platform.testing.subcutaneous.database

fun ClientInspection.sampleText(): String = client<SampleSource>()()

fun ClientInspection.sampleCount(): Int = client<SampleTally>().count

suspend fun ServerInspection.clearedLabels(): Int = database { SamplesTable.deleteAll() }

suspend fun ServerInspection.rawLabels(): String = database { exec("SELECT label FROM samples") { it.toString() }.orEmpty() }
