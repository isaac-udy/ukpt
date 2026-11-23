package platform.testing

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit.SECONDS

@Target(AnnotationTarget.CLASS)
@Tag("unit")
@Timeout(value = 10, unit = SECONDS)
actual annotation class UnitTest

@Target(AnnotationTarget.CLASS)
@Tag("subcutaneous")
@Timeout(value = 60, unit = SECONDS)
actual annotation class SubcutaneousTest

@Target(AnnotationTarget.CLASS)
@Tag("component")
@Timeout(value = 30, unit = SECONDS)
actual annotation class ComponentTest

@Target(AnnotationTarget.CLASS)
@Tag("snapshot")
actual annotation class SnapshotTest

@Target(AnnotationTarget.CLASS)
@Tag("wiring")
@Timeout(value = 30, unit = SECONDS)
actual annotation class WiringTest

@Target(AnnotationTarget.CLASS)
@Tag("adapter")
@Timeout(value = 60, unit = SECONDS)
actual annotation class AdapterTest

@Target(AnnotationTarget.CLASS)
@Tag("check")
@Timeout(value = 30, unit = SECONDS)
actual annotation class CheckTest

@Target(AnnotationTarget.CLASS)
@Tag("end-to-end")
@Timeout(value = 120, unit = SECONDS)
actual annotation class EndToEndTest
