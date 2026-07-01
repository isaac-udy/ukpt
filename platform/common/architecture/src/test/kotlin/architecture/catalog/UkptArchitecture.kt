package architecture.catalog

import architecture.registry.*

/** Every group in the architecture catalog, in document order. The single list `verify` runs over. */
object UkptArchitecture {
    val all: List<RuleGroup> = listOf(
        ModuleRules,
        DomainLayer,
        UiLayer,
        DataLayer,
        ServicesLayer,
        FeatureRules,
        ProjectRules,
    )
}
