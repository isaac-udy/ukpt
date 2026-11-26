package architecture.rules.telemetry

import architecture.definitions.TestCategories
import dev.isaacudy.udytils.architecture.*

@Describe("""
    A top-level property initialized with `analyticsRules<AnalyticsEvent> { … }`, which builds
    [Analytics Events](#analytics-event), or `metricRules<Metric> { … }`, which builds
    [Metrics](#metric). Each rule starts from a selector — `onCalled(Ref::fn)`,
    `onConstructed(Type::class)`, `onScreen(Key::class)` or `construct(Constructs.X)` — and its
    lambda receives the instance as `it`: the call's receiver, the constructed instance or the
    screen's key. `span` is available in the lambda.

    - `event` records an analytics event; `timing` the duration of a call or construction;
      `firstValue` the time from the start of a Flow's collection to its first value.
    - `.scoped { it.viewModelScope }` makes the next `event` or `timing` lambda suspend in the
      instance's own scope after the span ends. The rule records nothing when that scope is
      cancelled first, and a scoped `timing` measures from the span's start to the lambda's return.

    ```kotlin
    val clientAnalyticsRules = analyticsRules<AnalyticsEvent> {
        onScreen(UkptDestination::class).event(Trigger.Start) { AnalyticsEvent.UkptScreenViewed }
        onCalled(UkptViewModel::onGreetClicked).event(Trigger.Start) { AnalyticsEvent.GreetClicked }
    }

    val clientMetricRules = metricRules<Metric> {
        onCalled(Greet::invoke).timing { Metric.GreetDuration(succeeded = span.outcome == Outcome.Returned) }
        onConstructed(UkptViewModel::class).scoped { it.viewModelScope }.timing { viewModel ->
            viewModel.state.asStateFlow().first { it.greetingSummary.isSuccess() }
            Metric.UkptScreenLoaded
        }
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
