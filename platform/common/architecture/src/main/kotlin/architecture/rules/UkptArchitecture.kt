package architecture.rules

import architecture.definitions.isFeatureModule
import architecture.projectScope
import dev.isaacudy.udytils.architecture.*
import architecture.rules.data.DataLayer
import architecture.rules.domain.DomainLayer
import architecture.rules.feature.FeatureRules
import architecture.rules.module.ModuleRules
import architecture.rules.project.ProjectRules
import architecture.rules.services.ServicesLayer
import architecture.rules.ui.UiLayer

/**
 * UKPT's architecture definition: the rule groups in document order, the scope the rules govern,
 * and the docs layout. The [Describe] text is the README template — `{{toc}}` expands to the
 * generated doc list.
 */
@Describe("""
    # UKPT Architecture

    UKPT is a Kotlin Multiplatform template. Its architecture is built from vertical feature
    slices (`:feature:[name]:{api,client,server}`) over shared infrastructure (`:platform`),
    assembled by thin application shells (`:app`). Each feature is organised along four axes:
    `domain`, `ui`, `data`, and `services`. Module-graph rules keep the slices independent.

    The rules govern the feature modules. The composite builds (`embedded-enro`,
    `embedded-udytils`, and `build-logic`), test sources, and this rule module itself are not
    tested. `:feature:core` is the worked example the rules describe.
""")
object UkptArchitecture : ArchitectureDefinition(
    groups = listOf(
        ModuleRules,
        DomainLayer,
        UiLayer,
        DataLayer,
        ServicesLayer,
        FeatureRules,
        ProjectRules,
    ),
    scope = { projectScope },
    membership = { it.isFeatureModule() },
    docs = DocsConfig(module = "platform/common/architecture"),
)
