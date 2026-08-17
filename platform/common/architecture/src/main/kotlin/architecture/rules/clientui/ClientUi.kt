package architecture.rules.clientui

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.containsPackageSegment
import architecture.definitions.featureName
import architecture.definitions.featureNameFromContainingPackage
import architecture.definitions.isFeatureModule
import architecture.definitions.resolveTypeToken
import architecture.rules.shared.domainInterfaceFqnsOnSide
import architecture.utils.publishedDomainFqns
import architecture.utils.resolvesToPublishedFqn

@Describe("""
    `feature.[name].client.ui` — the client's outermost layer. It lives in `:client`: Compose UI
    ([Screens](#screen) and supporting composables), [ViewModels](#view-model), UI-state models,
    and the serializable Navigation Keys ([Destinations](#destination)) that open the screens. A
    Destination moves to `:api` (same package, a file move) only when another feature navigates to
    it — that published key is the one part of this layer a second feature may see.

    Everything the UI loads or mutates arrives through
    [domain interfaces](clientdomain.md#domain-interface), provided by
    [Repositories](clientdata.md#repository) in `client.data`. Server calls (via
    [Services](serverservices.md#service-interface)) reach the screen the same way.

    The layer rules below apply across the whole `feature.[name].client.ui` package.
""")
object ClientUi : RuleGroup(
    inPackage = "feature..client.ui..",
    constructs = listOf(
        Screen,
        Composable,
        Destination,
        ViewModel,
        ViewModelState,
        UiValueType,
        CompositionLocal,
    ),
) {

    // §3.2 ui package dependencies (layer-level — not tied to one construct)
    @Describe("The `client.ui` layer may depend on `client.domain`")
    val mayDependOnDomain by guidance

    @Describe("A `client.ui` file may import another feature's `client.domain` only when the imported declaration is published to `:api`")
    val crossFeatureDomainViaApi by rule {
        rationale(
            """
            `client.domain` is private to its feature except for what the feature publishes to
            `:api` (`ClientDomain.pure`). The UI reaches another feature's domain the same way it
            reaches everything else cross-feature: through the published surface, never the
            feature's own `:client` module.
            """.trimIndent(),
        )
        note("Reuses the same published-FQN channel as `ClientDomain.pure` and `ServerDomain.pure` — publishing is moving the file, not changing the package.")
        scope { scope, exempt ->
            val published = publishedDomainFqns(scope, "client.domain")
            scope.files
                .filter { it.isFeatureModule() && it.packagee?.name?.contains(".client.ui") == true }
                .filterNot { exempt(it) }
                .flatMap { file ->
                    val ownFeature = file.featureNameFromContainingPackage()
                    file.imports
                        .filter { it.name.contains(".client.domain.") }
                        .filter { it.featureName() != ownFeature }
                        .filterNot { resolvesToPublishedFqn(it.name, published) }
                        .map { Violation(file.path, "client.ui imports another feature's client.domain `${it.name}`, which is not published to `:api`") }
                }
        }
    }

    @Describe("The `client.ui` layer must never implement `domain` interfaces")
    val noImplementingDomainInterfaces by rule {
        rationale(
            """
            Domain interfaces are the contract between presentation and persistence; implementations
            belong in `client.data` (Repositories) or `client.domain` (UseCases). A ViewModel that
            implements one couples two layers' lifecycles and makes the ViewModel un-injectable
            elsewhere.
            """.trimIndent(),
        )
        note("A parent reference is resolved through its file's imports and matched against the client's classified [Domain Interfaces](clientdomain.md#domain-interface) by fully-qualified name — an `:api`-declared parent often resolves to no source declaration, so resolution-based testing would silently skip exactly the published contracts.")
        scope { scope, exempt ->
            val domainInterfaces = scope.domainInterfaceFqnsOnSide("client")
            scope.classes()
                .filter { it.isFeatureModule() }
                .filter { it.resideInPackage("feature..client.ui..") }
                .filterNot { exempt(it) }
                .filter { clazz ->
                    clazz.parents().any { parent ->
                        clazz.containingFile.resolveTypeToken(parent.name) in domainInterfaces
                    }
                }
                .map { Violation(it, "UI class implements a domain interface — implement it in `client.data` (Repository) or `client.domain` (UseCase) instead") }
        }
    }

    @Describe("The `client.ui` layer must never depend on `data` or `services`")
    val noDataServicesDeps by rule {
        rationale(
            """
            The UI consumes `client.domain` interfaces only. Repositories (in `client.data`) call
            `server.services` on the UI's behalf; the UI must not reach either directly.
            """.trimIndent(),
        )
        note("Tested over the import's package segments, so a `data` or `services` package is out of bounds wherever it sits and whichever feature owns it.")
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() && it.packagee?.name?.contains(".client.ui") == true }
                .filterNot { exempt(it) }
                .filter { file ->
                    file.imports.any { import ->
                        import.name.containsPackageSegment("data") ||
                            import.name.containsPackageSegment("services")
                    }
                }
                .map { Violation(it.path, "UI file imports a `data`/`services` package") }
        }
    }

    @Describe("The `client.ui` layer must not use `koinInject`: all dependencies are injected through ViewModels")
    val noKoinInject by rule {
        rationale(
            """
            Resolving from Koin inside a Composable bypasses the ViewModel as the single dependency
            surface, makes the screen untestable in snapshot tests (there is no Koin runtime), and
            re-resolves on every recomposition.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() && it.packagee?.name?.contains(".client.ui") == true }
                .filterNot { exempt(it) }
                .filter { file -> file.imports.any { it.name.contains("koinInject") } }
                .map { Violation(it.path, "UI file uses `koinInject`; inject through a ViewModel instead") }
        }
    }

    @Describe("Dialog destinations communicate with their opener through navigation results, not shared state or callbacks")
    val dialogsCommunicateViaResults by guidance {
        note("A dialog destination follows the same screen conventions as any other destination — it has its own ViewModel, and the ViewModel performs the navigation actions (`complete`/`requestClose` via its `navigationHandle`). Composables never reference the navigation handle directly.")
        note("A dialog destination is a `NavigationKey.WithResult<R>` with a meaningful result type. The opener registers a `NavigationResultChannel` via `ViewModel.registerForNavigationResult<R>` and opens the dialog with `channel.open(key)`. Dismissal without a result is a no-op — the opener's state does not change.")
        note("Navigation results are held in-memory (`NavigationResultChannel.pendingResults`), so custom result types need no serializers-module registration — unlike managed-flow step results, which persist via `polymorphic(Any)`.")
        note("Editor-style dialogs may own their submission (inline error/retry, `complete(result)` only on success) and complete with the fresh data so the opener updates without a refetch. Under `ProjectRules.noDirectAsyncStateConstruction` the opener cannot wrap a returned payload in `AsyncState.Success` directly, so in practice the result handler triggers a reload.")
    }

    @Describe("A `client.ui` package imports this layer only through its own package, its direct child subsystems, and its ancestors up to the layer root")
    val subsystemVisibility by rule {
        note("A `client.ui` subsystem is a screen family the rest of the UI reaches through one entry point — an onboarding flow's steps, a sign-in provider's screens. It needs no `client.domain` twin: the mirror restricts what a subsystem may import, not what must exist.")
        enforcedBy("ProjectRules.subsystemVisibility")
    }

    @Describe("A `client.ui` subsystem package imports `client.domain` only through its mirror subsystem, that subsystem's direct children, and their ancestors")
    val subsystemMirrorsDomain by rule {
        note("A file at the layer root is unconstrained — it sees the whole of its side's domain, as it always has. Only a file inside a subsystem package is bound to the mirror.")
        enforcedBy("ProjectRules.subsystemMirrorsDomain")
    }
}
