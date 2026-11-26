package architecture.rules.telemetry

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import dev.isaacudy.udytils.architecture.*

@Describe("""
    A `@Serializable sealed interface` in a telemetry module whose nested classes and objects are
    the analytics events that the [Trace Rules](#trace-rules) build. No event reaches a vendor
    directly: the project's analytics sink maps each event to the vendor's event name and
    properties in hand-written code.

    ```kotlin
    val name = when (event) {
        AnalyticsEvent.GreetClicked -> "greet_clicked"
        AnalyticsEvent.GreetingSent -> "greeting_sent"
        AnalyticsEvent.GreetingFailed -> "greeting_failed"
    }
    ```
""")
object AnalyticsEvent : Construct<Telemetry>(
    testedBy = TestExpectation.none("a value type; the Subcutaneous Tests of the Trace Rules assert which events are recorded"),
    requirements = listOf(isInterface, isSealed, isAnnotatedWith("Serializable"), inTelemetryModule),
) {
    @Describe("Every event of an Analytics Event hierarchy must be `@Serializable`")
    val serializableEvents by rule {
        rationale("A sink that forwards events to the project's server serializes each event over urpc.")
        note("`ProjectRules.serialNamePinnedOnPolymorphicTypes` requires each event's `@SerialName`.")
        constrain { decl, _ ->
            val hierarchy = decl as KoInterfaceDeclaration
            (hierarchy.classes(includeNested = false) + hierarchy.objects(includeNested = false))
                .filterNot { (it as KoAnnotationProvider).hasAnnotationWithName("Serializable") }
                .map { Violation(it as KoBaseDeclaration, "is not `@Serializable`") }
        }
    }
}
