package architecture.rules.telemetry

import architecture.definitions.TestCategories
import dev.isaacudy.udytils.architecture.*

@Describe("""
    A top-level property initialized with `analyticsRules<AnalyticsEvent> { … }`, which builds
    [Analytics Events](#analytics-event), or `metricRules<Metric> { … }`, which builds the
    [Metrics](#metric) of timings: `timing` measures each call, and `firstValue` measures each
    collection of a Flow from its start to its first value.

    ```kotlin
    val clientAnalyticsRules = analyticsRules<AnalyticsEvent> {
        event(onScreen(UkptDestination::class), Trigger.Start) { AnalyticsEvent.UkptScreenViewed }
        event(on(UkptViewModel::onGreetClicked), Trigger.Start) { AnalyticsEvent.GreetClicked }
    }

    val clientMetricRules = metricRules<Metric> {
        timing(on(Greet::invoke)) { span -> Metric.GreetDuration(succeeded = span.outcome == Outcome.Returned) }
        firstValue(on(FlowOfGreetingSummary::invoke)) { Metric.GreetingSummaryLoad }
    }
    ```

    `rules.sink(TraceSiteTable, sink)` applies the rules as a span sink; the telemetry module's
    Koin module registers both sinks when the app starts.
""")
object TraceRules : Construct<Telemetry>(
    testedBy = TestExpectation.anyOf(TestCategories.Subcutaneous),
    requirements = listOf(
        isPropertyWhere("is initialized with `analyticsRules { … }` or `metricRules { … }`") { TRACE_RULES_INITIALIZER.containsMatchIn(it.text) },
        inTelemetryModule,
    ),
)
