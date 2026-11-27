package architecture.rules.telemetry

import architecture.rules.testing.isInTelemetryModule
import dev.isaacudy.udytils.architecture.*

@Describe("""
    The telemetry modules, `:app:client:telemetry` and `:app:server:telemetry`, hold the
    measurement rules of the application. Feature code contains no analytics or timing calls: the
    trace compiler plugin records a span for each call of a feature function, and the
    [Trace Rules](#trace-rules) select spans and turn them into [Analytics Events](#analytics-event)
    and [Metrics](#metric).

    A rule selects the calls of a declaration, `onCalled(Greet::invoke)`, the constructions of a class,
    `onConstructed(HomeViewModel::class)`, the sites of a Construct,
    `construct(Constructs.ClientData.Repository)`, or a screen's visits, `onScreen(HomeKey::class)`. The telemetry module's build generates
    `TraceSiteTable` and `Constructs` from the features' site manifests and the architecture
    classification.

    The rule API and the telemetry modules are a beta: a later template version can change them.
""")
object Telemetry : RuleGroup(
    constructs = listOf(TraceRules, AnalyticsEvent, Metric),
) {
    @Describe("Only a telemetry module may declare trace rules")
    val rulesOnlyInTelemetryModules by rule {
        rationale("A measurement rule in feature code places measurement back in the code that the rule measures.")
        scope { scope, exempt ->
            scope.properties()
                .filter { TRACE_RULES_INITIALIZER.containsMatchIn(it.text) }
                .filterNot { it.isInTelemetryModule() }
                .filterNot { exempt(it) }
                .map { Violation(it, "declares trace rules outside an `:app:[side]:telemetry` module") }
        }
    }
}

internal val TRACE_RULES_INITIALIZER = Regex("""=\s*(analyticsRules|metricRules)\s*[<{]""")

internal val inTelemetryModule = Requirement("lives in an `:app:[side]:telemetry` module") { it.isInTelemetryModule() }
