package architecture.rules

import architecture.registry.*
import architecture.rules.data.DataLayer
import architecture.rules.domain.DomainLayer
import architecture.rules.feature.FeatureRules
import architecture.rules.module.ModuleRules
import architecture.rules.project.ProjectRules
import architecture.rules.services.ServicesLayer
import architecture.rules.ui.UiLayer

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
