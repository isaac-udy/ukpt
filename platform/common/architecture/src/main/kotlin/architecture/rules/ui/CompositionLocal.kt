package architecture.rules.ui

import dev.isaacudy.udytils.architecture.*

import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration

@Describe("""
    A top-level `Local…` [`CompositionLocal`](https://developer.android.com/jetpack/compose/compositionlocal)
    declared in a `..ui..` package — the Compose-native channel for supplying ambient behaviour to a
    composable without threading it as a parameter (for example a `LocalImageLoader` that lets a
    reusable component reach a DI-provided dependency without every call site passing it down). The
    value is provided once near the composition root and read by leaf composables.
""")
object CompositionLocal : Construct<UiLayer>(
    requirements = listOf(
        isProperty,
        predicate("is a top-level `Local…` val built via `compositionLocalOf` / `staticCompositionLocalOf`") { d ->
            d is KoPropertyDeclaration &&
                d.isTopLevel &&
                d.name.startsWith("Local") &&
                d.text.contains("ompositionLocalOf")
        },
    ),
) {
    @Describe("A composition local must be used as a dependency-access channel with an inert default (`null` / no-op), never as a back door for arbitrary mutable state")
    val dependencyAccessChannelWithInertDefault by rule {
        rationale(
            """
            An inert default lets a composable degrade gracefully when no provider is present, such as
            in snapshots and previews; using a composition local to smuggle mutable state around
            re-introduces the hidden coupling that threading dependencies through ViewModels avoids.
            """.trimIndent(),
        )
        unverifiable()
    }
}
