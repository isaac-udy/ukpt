package architecture.rules.ui

import dev.isaacudy.udytils.architecture.*
import architecture.rules.domain.DomainInterface

import architecture.definitions.containsPackageSegment
import architecture.definitions.isFeatureModule

@Describe("""
    The `ui` axis spans `:api` and `:client`. **`:api` contents**: serializable Navigation Keys
    (Destinations) — a feature's shared navigation entry points. **`:client` contents**: Compose UI
    (Screens and supporting composables), ViewModels, and UI-state models. Everything the UI loads
    or mutates arrives through [domain interfaces](domain.md#domain-interface), implemented by
    [Repositories](data.md#repository) in `data` — which is also how server calls (via
    [Services](services.md#service-interface)) reach the screen.

    The layer rules below apply across the whole `feature.[name].ui` package.
""")
object UiLayer : RuleGroup(
    inPackage = "feature..ui..",
    constructs = listOf(
        Screen,
        Composable,
        Destination,
        ViewModel,
        ViewModelState,
        UiValueType,
    ),
) {

    // §3.2 ui package dependencies (layer-level — not tied to one construct)
    @Describe("The `ui` layer may depend on `domain`")
    val mayDependOnDomain by guidance

    @Describe("The `ui` layer is forbidden from implementing `domain` interfaces")
    val noImplementingDomainInterfaces by rule {
        rationale(
            """
            Domain interfaces are the contract between presentation and persistence — implementations
            belong in `data` (Repositories) or `domain` (UseCases). A ViewModel that implements one
            would couple two layers' lifecycles and make the ViewModel un-injectable elsewhere.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.classes()
                .filter { it.isFeatureModule() }
                .filter { it.resideInPackage("..ui..") }
                .filterNot { exempt(it) }
                .filter { clazz ->
                    clazz.parents().any { parent ->
                        // Catalog construct (shape). The legacy rule also required the supertype to
                        // reside in feature..domain..; we approximate by shape, which is what the
                        // operator-invoke `fun interface` form effectively pins.
                        DomainInterface.test(parent)
                    }
                }
                .map { Violation(it, "UI class implements a domain interface — implement it in `data` (Repository) or `domain` (UseCase) instead") }
        }
    }

    @Describe("The `ui` layer is forbidden from depending on `data` or `services`")
    val noDataServicesDeps by rule {
        rationale(
            """
            UI consumes `domain` interactors only — Repositories (in `data`) fan out to `services`
            (the cross-the-wire contract) on the UI's behalf. The UI must not reach either directly.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() }
                .filter { it.packagee?.name?.contains(".ui") == true }
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

    @Describe("The `ui` layer must not use `koinInject` — all dependencies are injected through ViewModels")
    val noKoinInject by rule {
        rationale(
            """
            Resolving from Koin inside a Composable side-steps the ViewModel as the single dependency
            surface, makes the screen untestable in snapshots (no Koin runtime), and re-resolves on
            every recomposition.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() }
                .filter { it.packagee?.name?.containsPackageSegment("ui") == true }
                .filterNot { exempt(it) }
                .filter { file -> file.imports.any { it.name.contains("koinInject") } }
                .map { Violation(it.path, "UI file uses `koinInject`; inject through a ViewModel instead") }
        }
    }
}
