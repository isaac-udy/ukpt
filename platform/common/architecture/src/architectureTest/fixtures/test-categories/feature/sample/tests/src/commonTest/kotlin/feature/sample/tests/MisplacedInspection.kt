package feature.sample.tests

import platform.testing.subcutaneous.ServerInspection
import platform.testing.subcutaneous.database

suspend fun ServerInspection.labelCount(): Int = database { 0 }
