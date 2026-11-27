package architecture.rules.telemetry

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import dev.isaacudy.udytils.architecture.*

@Describe("""
    A `@Serializable sealed interface` in a telemetry module, named `[Name]Metric`, whose nested
    classes and objects are the timings that the metric [Trace Rules](#trace-rules) build. A
    metric's properties are its tags. The project's metrics sink maps each metric to the vendor's
    metric name and tags in hand-written code.

    ```kotlin
    val (name, tags) = when (metric) {
        is Metric.GreetDuration -> "greet.duration" to mapOf("succeeded" to metric.succeeded.toString())
        Metric.GreetingSummaryLoad -> "greeting_summary.load" to emptyMap()
    }
    ```
""")

object Metric : Construct<Telemetry>(
    testedBy = TestExpectation.none("a value type; the Subcutaneous Tests of the Trace Rules assert which metrics are recorded"),
    requirements = listOf(isInterface, isSealed, isAnnotatedWith("Serializable"), inTelemetryModule, hasNameEndingWith("Metric")),
) {
    @Describe("Every metric of a Metric hierarchy must be `@Serializable`")
    val serializableMetrics by rule {
        rationale("A sink that forwards metrics to the project's server serializes each metric over urpc.")
        note("`ProjectRules.serialNamePinnedOnPolymorphicTypes` requires each metric's `@SerialName`.")
        constrain { decl, _ ->
            val hierarchy = decl as KoInterfaceDeclaration
            (hierarchy.classes(includeNested = false) + hierarchy.objects(includeNested = false))
                .filterNot { (it as KoAnnotationProvider).hasAnnotationWithName("Serializable") }
                .map { Violation(it as KoBaseDeclaration, "is not `@Serializable`") }
        }
    }
}
