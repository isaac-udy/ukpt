package architecture.rules.telemetry

import architecture.definitions.TestCategories
import dev.isaacudy.udytils.architecture.*

@Describe("""
    A top-level property initialized with `traceRules<[Event]> { … }`: the analytics events,
    timings and first-value timings of one side of the application.

    ```kotlin
    val clientTraceRules = traceRules<AnalyticsEvent> {
        event(on(UkptViewModel::onGreetClicked), Trigger.Start) { AnalyticsEvent.GreetClicked }
        timing(construct(Constructs.ClientData.Repository))
        firstValue(on(FlowOfGreetingSummary::invoke))
    }
    ```

    `rules.sink(TraceSiteTable, analytics, metrics)` applies the rules as a span sink.
""")
object TraceRules : Construct<Telemetry>(
    testedBy = TestExpectation.anyOf(TestCategories.Subcutaneous),
    requirements = listOf(
        isPropertyWhere("is initialized with `traceRules { … }`") { TRACE_RULES_INITIALIZER.containsMatchIn(it.text) },
        inTelemetryModule,
    ),
)
