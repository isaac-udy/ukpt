package architecture.rules.telemetry

import architecture.rules.testing.isInTelemetryModule
import dev.isaacudy.udytils.architecture.*

@Describe("""
    The telemetry modules, `:app:client:telemetry` and `:app:server:telemetry`, hold the
    measurement rules of the application. Feature code contains no analytics or timing calls: the
    trace compiler plugin records a span for each call of a feature function, and the
    [Trace Rules](#trace-rules) select spans and turn them into [Analytics Events](#analytics-event)
    and timings.

    A rule selects sites by declaration, `on(Greet::invoke)`, or by Construct,
    `construct(Constructs.ClientData.Repository)`. The telemetry module's build generates
    `TraceSiteTable` and `Constructs` from the features' site manifests and the architecture
    classification.
""")
object Telemetry : RuleGroup(
    constructs = listOf(TraceRules, AnalyticsEvent),
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

internal val TRACE_RULES_INITIALIZER = Regex("""=\s*traceRules\s*[<{]""")

internal val inTelemetryModule = Requirement("lives in an `:app:[side]:telemetry` module") { it.isInTelemetryModule() }
