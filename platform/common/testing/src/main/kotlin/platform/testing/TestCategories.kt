package platform.testing

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit.SECONDS

/** One production class exercised over many inputs; collaborators are test fakes or lambdas. */
@Target(AnnotationTarget.CLASS)
@Tag("unit")
@Timeout(value = 10, unit = SECONDS)
annotation class UnitTest

/** Pages driven through robots against the production graph, a real in-process server and database. */
@Target(AnnotationTarget.CLASS)
@Tag("subcutaneous")
@Timeout(value = 60, unit = SECONDS)
annotation class SubcutaneousTest

/** Renders one page, component or event stream with fixed state and reads the markup. */
@Target(AnnotationTarget.CLASS)
@Tag("component")
@Timeout(value = 30, unit = SECONDS)
annotation class ComponentTest

/** Renders pages and compares them with golden HTML. */
@Target(AnnotationTarget.CLASS)
@Tag("snapshot")
@Timeout(value = 30, unit = SECONDS)
annotation class SnapshotTest

/** Resolves a dependency-injection graph. */
@Target(AnnotationTarget.CLASS)
@Tag("wiring")
@Timeout(value = 30, unit = SECONDS)
annotation class WiringTest

/** One infrastructure adapter against the real infrastructure. */
@Target(AnnotationTarget.CLASS)
@Tag("adapter")
@Timeout(value = 60, unit = SECONDS)
annotation class AdapterTest

/** Two committed artifacts agree. */
@Target(AnnotationTarget.CLASS)
@Tag("check")
@Timeout(value = 30, unit = SECONDS)
annotation class CheckTest

/** The server on a real port, driven through a real browser. */
@Target(AnnotationTarget.CLASS)
@Tag("end-to-end")
@Timeout(value = 120, unit = SECONDS)
annotation class EndToEndTest
