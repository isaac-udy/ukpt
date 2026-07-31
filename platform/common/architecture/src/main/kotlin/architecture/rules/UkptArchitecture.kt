package architecture.rules

import architecture.definitions.isFeatureModule
import architecture.projectScope
import dev.isaacudy.udytils.architecture.*
import architecture.rules.clientdata.ClientData
import architecture.rules.clientdomain.ClientDomain
import architecture.rules.clientui.ClientUi
import architecture.rules.designsystem.DesignSystemRules
import architecture.rules.feature.FeatureRules
import architecture.rules.module.ModuleRules
import architecture.rules.project.ProjectRules
import architecture.rules.serverdata.ServerData
import architecture.rules.serverdomain.ServerDomain
import architecture.rules.serverservices.ServerServices

/**
 * UKPT's architecture definition: the rule groups in document order, the scope the rules govern,
 * and the docs layout. The [Describe] text is the README template — `{{toc}}` expands to the
 * generated doc list.
 */
@Describe("""
    # UKPT Architecture

    UKPT is a Kotlin Multiplatform template. Its architecture is built from vertical feature
    slices (`:feature:[name]:{api,client,server}`) over shared infrastructure (`:platform`),
    assembled by thin application shells (`:app`). Module-graph rules keep the slices independent.

    A declaration's **package** says what it is; the Gradle **module** it lives in says who may see
    it. A feature is rooted at `feature.[name]`, which holds its shared language — the domain models
    both sides speak. One level down is a side, `client` or `server`; two levels down is a layer
    within that side. The deeper the package, the more private the code.

    ```
    client.ui → client.domain ← client.data → [ contract ] ← server.services → server.domain ← server.data
    ```

    Two hexagons around pure cores, joined at the RPC contract. Each side's `domain` sits between
    its consumers and its adapters and knows neither, so `client.data` and `server.services` are the
    mirror pair at the network edge, and a `Repository` provides the domain interfaces on both sides
    — reading through a Service on the client and a `StorageClass` over a table on the server. The
    network is the only connection between sides, and `client.data` is the only client package that
    may import its contracts.

    The rules govern the feature modules. The composite builds (`embedded-enro`,
    `embedded-udytils`, and `build-logic`), test sources, and this rule module itself are not
    tested. `:feature:core` is the worked example the rules describe: it keeps its feature code in
    `feature.[name]` package namespaces so each slice stays liftable into its own module.

    Rules land enforced from their first commit, never as audits, and no declaration carries an
    `@ArchitectureException`. A rule that cannot be met is a design question, not a setting.
""")
object UkptArchitecture : ArchitectureDefinition(
    groups = listOf(
        ModuleRules,
        FeatureRules,
        ClientDomain,
        ClientData,
        ClientUi,
        ServerServices,
        ServerDomain,
        ServerData,
        DesignSystemRules,
        ProjectRules,
    ),
    scope = { projectScope },
    membership = { it.isFeatureModule() },
    docs = DocsConfig(module = "platform/common/architecture"),
)
