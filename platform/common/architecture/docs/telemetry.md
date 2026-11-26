> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/architecture/rules/telemetry/` and the `*.examples.md` files beside them.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# [Telemetry](../src/main/kotlin/architecture/rules/telemetry/Telemetry.kt)

The telemetry modules, `:app:client:telemetry` and `:app:server:telemetry`, hold the
measurement rules of the application. Feature code contains no analytics or timing calls: the
trace compiler plugin records a span for each call of a feature function, and the
[Trace Rules](#trace-rules) select spans and turn them into [Analytics Events](#analytics-event)
and timings.

A rule selects sites by declaration, `on(Greet::invoke)`, or by Construct,
`construct(Constructs.ClientData.Repository)`. The telemetry module's build generates
`TraceSiteTable` and `Constructs` from the features' site manifests and the architecture
classification.

##### Constructs

* [Trace Rules](#trace-rules)
* [Analytics Event](#analytics-event)

##### Rules

* Only a telemetry module may declare trace rules
    * **Why:** A measurement rule in feature code places measurement back in the code that the rule measures.

---

## [Trace Rules](../src/main/kotlin/architecture/rules/telemetry/TraceRules.kt)

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

##### Requirements

* A Trace Rules is initialized with `traceRules { … }`
* A Trace Rules lives in an `:app:[side]:telemetry` module

##### Tested by

* Subcutaneous

---

## [Analytics Event](../src/main/kotlin/architecture/rules/telemetry/AnalyticsEvent.kt)

A `@Serializable sealed interface` in a telemetry module whose nested classes and objects are
the analytics events that the [Trace Rules](#trace-rules) build. Each event's `@SerialName`,
such as `"AnalyticsEvent.GreetClicked"`, names the event; an analytics sink derives the
vendor's event name from the serial name.

##### Requirements

* An Analytics Event is an interface
* An Analytics Event is `sealed`
* An Analytics Event is annotated `@Serializable`
* An Analytics Event lives in an `:app:[side]:telemetry` module

##### Tested by

* No tests are expected: a value type; the Subcutaneous Tests of the Trace Rules assert which events are recorded

##### Rules

* Every event of an Analytics Event hierarchy must be `@Serializable`
    * **Why:** An analytics sink sends each event as its serial name and its serialized properties.
    * **Note:** `ProjectRules.serialNamePinnedOnPolymorphicTypes` requires each event's `@SerialName`.
