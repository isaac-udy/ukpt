package architecture.rules

import architecture.registry.RuleGroup

/**
 * The UKPT architecture catalog — the single source of truth for every rule: its path id,
 * statement, rationale, enforcement, and (via the README generator) its documentation.
 *
 * Each layer/group lives in its own `*Rules.kt` file and is assembled here. `verify(all)` runs the
 * whole catalog; the README index is generated from it.
 */
object UkptArchitecture {
    val all: List<RuleGroup> = listOf(
        domainLayer,
        uiLayer,
        dataLayer,
        servicesLayer,
        featureRules,
        projectRules,
        moduleRules,
    )
}
