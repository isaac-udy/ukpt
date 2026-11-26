> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/telemetry/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Telemetry](../src/main/kotlin/architecture/rules/telemetry/Telemetry.kt)

The telemetry modules, `:app:client:telemetry` and `:app:server:telemetry`, hold the
measurement rules of the application. Feature code contains no analytics or timing calls: the
trace compiler plugin records a span for each call of a feature function, and the
[Trace Rules](#trace-rules) select spans and turn them into [Analytics Events](#analytics-event)
and [Metrics](#metric).

A rule selects sites by declaration, `on(Greet::invoke)`, or by Construct,
`construct(Constructs.ClientData.Repository)`, or a screen's visits, `onScreen(HomeKey::class)`. The telemetry module's build generates
`TraceSiteTable` and `Constructs` from the features' site manifests and the architecture
classification.

##### Constructs

* [Trace Rules](#trace-rules)
* [Analytics Event](#analytics-event)
* [Metric](#metric)

##### Rules

* Only a telemetry module may declare trace rules
    * **Why:** A measurement rule in feature code places measurement back in the code that the rule measures.

---

## [Trace Rules](../src/main/kotlin/architecture/rules/telemetry/TraceRules.kt)

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

##### Requirements

* A Trace Rules is initialized with `analyticsRules { … }` or `metricRules { … }`
* A Trace Rules lives in an `:app:[side]:telemetry` module

##### Tested by

* Subcutaneous

---

## [Analytics Event](../src/main/kotlin/architecture/rules/telemetry/AnalyticsEvent.kt)

A `@Serializable sealed interface` in a telemetry module, named `[Name]Event`, whose nested
classes and objects are the analytics events that the [Trace Rules](#trace-rules) build. No event reaches a vendor
directly: the project's analytics sink maps each event to the vendor's event name and
properties in hand-written code.

```kotlin
val name = when (event) {
    AnalyticsEvent.UkptScreenViewed -> "ukpt_screen_viewed"
    AnalyticsEvent.GreetClicked -> "greet_clicked"
    AnalyticsEvent.GreetingSent -> "greeting_sent"
    AnalyticsEvent.GreetingFailed -> "greeting_failed"
}
```

##### Requirements

* An Analytics Event is an interface
* An Analytics Event is `sealed`
* An Analytics Event is annotated `@Serializable`
* An Analytics Event lives in an `:app:[side]:telemetry` module
* An Analytics Event is named `[Name]Event`

##### Tested by

* No tests are expected: a value type; the Subcutaneous Tests of the Trace Rules assert which events are recorded

##### Rules

* Every event of an Analytics Event hierarchy must be `@Serializable`
    * **Why:** A sink that forwards events to the project's server serializes each event over urpc.
    * **Note:** `ProjectRules.serialNamePinnedOnPolymorphicTypes` requires each event's `@SerialName`.

---

## [Metric](../src/main/kotlin/architecture/rules/telemetry/Metric.kt)

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

##### Requirements

* A Metric is an interface
* A Metric is `sealed`
* A Metric is annotated `@Serializable`
* A Metric lives in an `:app:[side]:telemetry` module
* A Metric is named `[Name]Metric`

##### Tested by

* No tests are expected: a value type; the Subcutaneous Tests of the Trace Rules assert which metrics are recorded

##### Rules

* Every metric of a Metric hierarchy must be `@Serializable`
    * **Why:** A sink that forwards metrics to the project's server serializes each metric over urpc.
    * **Note:** `ProjectRules.serialNamePinnedOnPolymorphicTypes` requires each metric's `@SerialName`.
