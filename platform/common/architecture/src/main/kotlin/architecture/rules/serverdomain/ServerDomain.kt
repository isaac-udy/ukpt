package architecture.rules.serverdomain

import architecture.rules.shared.DomainGroupRules
import dev.isaacudy.udytils.architecture.*

@Describe("""
    `feature.[name].server.domain` — the server's private logic, and the layer that makes the
    mirrored hexagon work.

    ```
    client.ui → client.domain ← client.data → [ contract ] ← server.services → server.domain ← server.data
    ```

    `server.services` and `server.data` **never see each other**. Domain sits between them and knows
    neither: services consume [domain interfaces](#domain-interface), and
    [Repositories](serverdata.md#repository) provide them, exposing each one as a `public val` and
    reading through the [StorageClasses](serverdata.md#storage-class) that own the tables. A
    [Repository](clientdata.md#repository) does the same job on the client — one pattern, learned
    once, appearing on both sides. An [IntegrationClient](serverdata.md#integration-client) provides
    a domain interface the same way when what satisfies it is outside the process rather than in a
    table.

    A domain interface published to `:api` is what one feature offers another. There is no separate
    "operation" or "query" concept: whether a contract is called across a feature boundary is a
    *publication* question, answered by which module the file sits in, as everywhere else.

    Around the contracts sit the layer's supporting shapes: [domain models](#domain-model),
    [extension functions](#extension-function) and [extension properties](#extension-property) that
    add derived behaviour to one, and a [constants](#constants) object for the values this side's
    logic agrees on.

    Two properties are structural rather than rule-enforced: a domain interface **cannot** touch a
    table and **cannot** inject request-scoped authentication, because this layer imports feature
    roots only. A storage function reached from `services` is expressed here as a domain interface,
    or folded with its siblings into a [UseCase](#use-case) when the logic spans several.
""")
object ServerDomain : DomainGroupRules(
    side = "server",
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

    @Describe("The `server.domain` layer must import feature roots and `:api`-published `server.domain` declarations only")
    val pure by rule {
        rationale(
            """
            Domain is the middle of the hexagon and knows neither edge. Importing `server.data` would
            couple logic to persistence; importing `server.services` would drag the wire contract and
            request scope into it, and would let a domain interface reach the very thing that is
            supposed to consume it.
            """.trimIndent(),
        )
        note("This is what makes `noTables` and `noAuth` unnecessary as separate rules — neither is reachable from here.")
        note("Other features' roots are importable — real vocabularies reference each other. Another feature's server.domain interfaces and models are importable when their declaration resides in `:api`; implementations are never published, so they are never importable across features.")
        note("A file's own feature's `server.domain` is the layer itself, so it is not an import out of the layer; the exemption is scoped to the importing file's feature and to no other.")
        scope(pureCheck())
    }

    @Describe("The `server.domain` layer must not contain persistence or transport dependencies, such as Exposed, Ktor, or SQL")
    val noPlatformDeps by rule {
        rationale("Pure logic stays testable without a database or a request. Declare a domain interface and let `server.data` satisfy it.")
        note("A generated Exposed table (`platform.server.postgres.tables.**`) counts as a persistence dependency — naming one is naming a column, whatever the package reads as. The project's UI-carrying platform modules (`platform.design.**`, `platform.ui.**`) count too. Pure cross-cutting primitives from other platform modules — a logger, an auth credential, `platform.server.postgres.TransactionRunner` — are legitimate here.")
        scope(noPlatformDepsCheck("server.domain file imports a persistence or transport dependency"))
    }

    @Describe("A `server.domain` interface that another feature calls must be declared in the `:api` module")
    val publishedInterfacesInApi by rule {
        note("Publishing is moving the file between modules — the package is unchanged, so no import churn.")
        note("The layer root is the whole of the publication channel: a subsystem declaration is never published (`ModuleRules.subsystemsNotPublished`). A capability a subsystem computes that another feature needs is restated as a root contract the subsystem satisfies.")
        enforcedBy("ModuleRules.crossFeatureCodeViaApi")
    }

    @Describe("A `server.domain` package imports this layer only through its own package, its direct child subsystems, and its ancestors up to the layer root")
    val subsystemVisibility by rule {
        note("A subsystem is a capability the feature has that nothing outside it names — the framework of a processing pipeline, an audio subtree. It earns a package at the point where a reader scanning the layer root has to skip past it, and the constructs classify inside one exactly as they do at the root: a subsystem is a location, not a kind of thing.")
        note("Composition across two subsystems belongs to their shared ancestor, where a shared payload is an ordinary [domain model](#domain-model) and a shared contract an ordinary [domain interface](#domain-interface).")
        enforcedBy("ProjectRules.subsystemVisibility")
    }
}
