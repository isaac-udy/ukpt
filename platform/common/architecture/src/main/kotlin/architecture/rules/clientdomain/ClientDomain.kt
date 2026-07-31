package architecture.rules.clientdomain

import architecture.rules.shared.DomainGroupRules
import dev.isaacudy.udytils.architecture.*

@Describe("""
    `feature.[name].client.domain` — the client's private logic. Single-function
    [domain interfaces](clientdomain.md#domain-interface) that [ViewModels](clientui.md#view-model) consume and
    [Repositories](clientdata.md#repository) provide, [UseCases](#use-case) composing several of them, and
    [domain models](#domain-model) that never leave the client. Around those,
    [extension functions](#extension-function) and [extension properties](#extension-property) add
    derived behaviour to a model, and a [constants](#constants) object holds the values this side's
    logic agrees on.

    It is **pure**: it may import feature roots and nothing else. No Compose, no Ktor, no
    persistence, no service contracts. That purity is what makes it testable without a harness, and
    what stops client abstractions leaking into the wire vocabulary.

    A domain interface may be **published to `:api`** when another feature's UI needs it; use cases
    and domain models stay in `:client`. Publishing is moving the file, not changing the package.

    This layer is the exact mirror of [`server.domain`](serverdomain.md) — same construct names,
    same rules, opposite side. The layer supplies the context, so the names never repeat it.
""")
object ClientDomain : DomainGroupRules(
    side = "client",
    constructs = listOf(
        DomainInterface,
        UseCase,
        DomainModel,
        ExtensionFunction,
        ExtensionProperty,
        Constants,
        Workflow,
        WorkflowStep,
        DomainException,
    ),
) {

    @Describe("The `client.domain` layer must import feature roots and `:api`-published `client.domain` declarations only")
    val pure by rule {
        rationale(
            """
            Domain sits between the UI and the data adapter and knows neither. Importing `client.data`
            inverts the dependency, importing `client.ui` cycles it, and importing `server.**` breaks
            the side boundary outright.
            """.trimIndent(),
        )
        note("Other features' client.domain interfaces and models are importable when their declaration resides in `:api`; implementations are never published, so they are never importable across features.")
        note("A file's own feature's `client.domain` is the layer itself, so it is not an import out of the layer; the exemption is scoped to the importing file's feature and to no other.")
        scope(pureCheck())
    }

    @Describe("The `client.domain` layer must not contain platform-specific dependencies, such as Android, Compose, Ktor, or SQL")
    val noPlatformDeps by rule {
        rationale(
            """
            The layer stays pure Kotlin so it compiles for every KMP target and stays unit-testable.
            Expose a domain interface and implement it in `client.data` instead.
            """.trimIndent(),
        )
        note("A generated Exposed table (`platform.server.postgres.tables.**`) counts as a platform dependency — naming one is naming a column, whatever the package reads as. So do the project's UI-carrying platform modules (`platform.design.**`, `platform.ui.**`): their types are Compose-backed. Pure cross-cutting primitives from other platform modules — a logger, an auth credential — are legitimate here.")
        scope(noPlatformDepsCheck("client.domain file imports a platform-specific dependency"))
    }

    @Describe("The `client.domain` layer may depend on another feature's root, but only via that feature's `:api` module")
    val crossFeatureViaApi by rule {
        enforcedBy("ModuleRules.clientApiOnly", "ModuleRules.crossFeatureCodeViaApi")
    }

    @Describe("A `client.domain` package imports this layer only through its own package, its direct child subsystems, and its ancestors up to the layer root")
    val subsystemVisibility by rule {
        note("A subsystem package is a capability of the feature that nothing outside it names, and it is never published (`ModuleRules.subsystemsNotPublished`): the constructs classify inside one exactly as they do at the layer root, because a subsystem is a location rather than a kind of thing.")
        note("Composition across two subsystems belongs to their shared ancestor, where a shared payload is an ordinary [domain model](#domain-model) and a shared contract an ordinary [domain interface](#domain-interface).")
        enforcedBy("ProjectRules.subsystemVisibility")
    }
}
