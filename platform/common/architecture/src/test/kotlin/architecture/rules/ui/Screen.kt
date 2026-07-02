package architecture.rules.ui

import architecture.registry.*

import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider

@Describe("""
    A Composable function (or property-based `navigationDestination`) that defines the layout
    and visual representation of a feature or portion of a feature.

    ### Dialog / Overlay Screens

    A Screen that is presented as a dialog or overlay on top of the current screen, rather
    than pushing onto the navigation backstack — governed by the `UiLayer.Screen.overlayViaDsl`
    and `UiLayer.Screen.overlayViewModel` rules below. Regular screens that push to the
    backstack should use the standard `@Composable fun` pattern; the property-based
    `navigationDestination` DSL is specifically for screens that need to declare custom
    metadata (such as `directOverlay()`). The property name may end in `Screen` or
    `Destination` — both are accepted because the property *is* the destination declaration
    site.
""")
object Screen : Construct<UiLayer>(
    requirements = listOf(
        predicate("is bound to its Destination via the `@NavigationDestination` annotation") { declaration ->
            require(declaration is KoNameProvider)
            require(declaration is KoAnnotationProvider)
            require(declaration is KoPropertyDeclaration || declaration is KoFunctionDeclaration)
            require(declaration.isTopLevel)

            declaration.hasAnnotation { annotation ->
                listOf(
                    "NavigationDestination",
                    "NavigationDestination.PlatformOverride",
                ).any { annotation.text.contains(it) }
            }
        },
        predicate("is named `[Name]Screen` (property-based screens may end in `Screen` or `Destination`)") { declaration ->
            require(declaration is KoNameProvider)
            require(declaration is KoAnnotationProvider)
            require(declaration is KoPropertyDeclaration || declaration is KoFunctionDeclaration)
            require(declaration.isTopLevel)

            val nameWithoutPlatformSuffix = declaration.name
                .removeSuffix("Wasm")
                .removeSuffix("Desktop")
                .removeSuffix("Ios")
                .removeSuffix("Android")

            // Two valid shapes:
            //  - `@Composable fun XxxScreen(...)` — function form, name must end "Screen".
            //  - `val xxxScreen|xxxDestination = navigationDestination<...>()` — property
            //    form, used when the destination needs metadata. Properties may end "Screen"
            //    or "Destination" — both are accepted because the property *is* the
            //    destination declaration site.
            when (declaration) {
                is KoFunctionDeclaration -> nameWithoutPlatformSuffix.endsWith("Screen")
                is KoPropertyDeclaration ->
                    nameWithoutPlatformSuffix.endsWith("Screen") ||
                        nameWithoutPlatformSuffix.endsWith("Destination")
                else -> false
            }
        },
        predicate("has a single parameter — the associated `[Name]ViewModel` (property form exempt)") { declaration ->
            when (declaration) {
                is KoFunctionDeclaration ->
                    declaration.parameters.size == 1 &&
                        declaration.parameters.single().type.name.endsWith("ViewModel")
                is KoPropertyDeclaration -> true // Property-based screens don't have parameters
                else -> false
            }
        },
    ),
) {
    @Describe("A Screen function must be annotated with `@Composable`")
    val composableFunction by rule {
        constrain { decl, _ ->
            val fn = decl as? KoFunctionDeclaration ?: return@constrain emptyList() // property-form screens have no annotation to check
            if (fn.hasAnnotationWithName("Composable")) {
                emptyList()
            } else {
                listOf(Violation(fn, "Screen function `${fn.name}` is missing `@Composable`"))
            }
        }
    }

    @Describe("A Screen function has a 1:1 relationship with a ViewModel and ViewModel State")
    val viewModelStateRelationship by guidance
    @Describe("A Screen function must observe the ViewModel's `state` property and use it to drive the UI")
    val observesState by guidance {
        audit { decl, _ ->
            val fn = decl as? KoFunctionDeclaration ?: return@audit emptyList()
            if (fn.text.contains(".state")) {
                emptyList()
            } else {
                listOf(Violation(fn, "Screen function does not appear to read `viewModel.state`"))
            }
        }
    }
    @Describe("A Screen function should delegate all user interaction handling to the ViewModel")
    val delegatesInteraction by guidance
    @Describe("A dialog/overlay Screen must use the `navigationDestination` DSL with `metadata = { directOverlay() }`")
    val overlayViaDsl by guidance
    @Describe("A dialog/overlay Screen that needs a ViewModel should call `viewModel()` inside the `navigationDestination` block")
    val overlayViewModel by guidance

    @Describe("A Screen function must be paired with an `internal [Name]ScreenContent` composable in the same file")
    val screenContentCompanion by rule {
        rationale(
            """
            The Screen function plumbs the ViewModel; the `ScreenContent` function takes only
            state + callbacks so snapshot tests can render every state without a ViewModel.
            Marking it `internal` lets the host-test source set call it; `private` makes the
            screen untestable.
            """.trimIndent(),
        )
        constrain { decl, _ ->
            val file = (decl as? KoContainingFileProvider)?.containingFile ?: return@constrain emptyList()
            val baseName = file.nameWithExtension.removeSuffix(".kt")
            if (!baseName.endsWith("Screen")) return@constrain emptyList()
            val expected = "${baseName}Content"
            val hasContent = file.functions().any { fn ->
                fn.name == expected &&
                    fn.hasInternalModifier &&
                    fn.hasAnnotationWithName("Composable")
            }
            if (hasContent) emptyList() else listOf(Violation(decl, "Screen is missing its `internal $expected` @Composable in the same file"))
        }
    }

    @Describe("A ViewModel must be injected into its Screen using `viewModel()`, not `koinViewModel()`")
    val viewModelInjection by rule {
        rationale(
            """
            `viewModel()` ties the ViewModel's lifecycle to the navigation backstack entry — when
            the entry is popped, the ViewModel is cleared. `koinViewModel()` resolves through Koin
            and either scopes to the wrong lifecycle or returns a singleton, leaking state between
            screens or returning stale state on re-entry.
            """.trimIndent(),
        )
        constrain { decl, _ ->
            val file = (decl as? KoContainingFileProvider)?.containingFile ?: return@constrain emptyList()
            if (file.imports.any { it.name.contains("koinViewModel") }) {
                listOf(Violation(decl, "Screen file imports `koinViewModel`; inject ViewModels via `viewModel()`"))
            } else {
                emptyList()
            }
        }
    }
}
