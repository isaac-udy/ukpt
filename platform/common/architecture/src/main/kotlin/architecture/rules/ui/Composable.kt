package architecture.rules.ui

import dev.isaacudy.udytils.architecture.*

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration

@Describe("""
    A `@Composable` function defined in the `..ui..` package that is not a [Screen](#screen).
    Typically a sub-component used by one or more screens, an inline editor, or a
    feature-specific overlay.

    * **Note:** `[Name]ScreenContent` companions (see `UiLayer.Screen.screenContentCompanion`)
      are non-Screen composables, which is why the snapshot-test rule lives on this Construct.
      For reusable design-system primitives (buttons, fields), prefer a shared composable in
      `:platform:client:ui`. Feature-local composables live alongside the Screen they support,
      and may be `internal` so snapshot tests can drive them.

    ### Snapshot tests

    A [Paparazzi](https://github.com/cashapp/paparazzi) host-side test renders a Screen's
    `[Name]ScreenContent` and records a golden image, catching visual regressions without a
    device or emulator. This is enforced by `UiLayer.Composable.screenContentSnapshotTest` below.

    * **Note:** Snapshot tests live in `feature/.../src/androidHostTest/` (the host-test source
      set under AGP 9.0's KMP library plugin) and use the `SnapshotRule` helper
      (`platform.snapshot.SnapshotRule`):
        * `snapshot.screen { ... }`: for screen content and composables that need bounded layout
          constraints, such as `fillMaxSize()`. Renders in a fixed-size container.
        * `snapshot.component { ... }`: for small, self-sizing composables. Renders at content
          size with padding.
    * **Note:** The composable under test must be `internal` (not `private`) so the host-test
      source set can reach it; this is the same constraint `UiLayer.Screen.screenContentCompanion`
      enforces. Add a `@Test` per meaningful state (loaded, empty, error) as a screen grows.
    * **Note:** Record golden images after adding or changing a snapshot test, then verify they
      match (goldens are committed under `src/androidHostTest/snapshots/images/`):

      ```
      ./gradlew :feature:core:client:recordPaparazzi
      ./gradlew :feature:core:client:verifyPaparazzi
      ```
""")
object Composable : Construct<UiLayer>(
    requirements = listOf(
        predicate("is not a Screen") { declaration -> !Screen.test(declaration) },
        isAnnotatedWith("Composable"),
    ),
) {
    @Describe("A `[Name]ScreenContent` composable must be exercised by at least one snapshot test")
    val screenContentSnapshotTest by rule {
        rationale(
            """
            `ScreenContent` exists so the screen body can be rendered from state and callbacks.
            The test only verifies that each ScreenContent is called from a `@Test` in an
            `androidHostTest` source set; it does not require a minimum number of snapshots.
            """.trimIndent(),
        )
        constrain { decl, _ ->
            val fn = decl as? KoFunctionDeclaration ?: return@constrain emptyList()
            if (!fn.name.endsWith("ScreenContent")) return@constrain emptyList()
            if (!fn.resideInPackage("feature..ui..")) return@constrain emptyList()
            // Snapshot tests live under `src/androidHostTest/`, which `projectScope` excludes —
            // scan those files directly for a reference to each ScreenContent.
            val tested = snapshotTestSources.any { source -> source.contains("${fn.name}(") }
            if (tested) emptyList() else listOf(Violation(fn, "ScreenContent has no snapshot test that calls `${fn.name}(`"))
        }
    }
}

/**
 * Snapshot tests live under `src/androidHostTest/`, which `projectScope` deliberately excludes, so
 * `Composable.screenContentSnapshotTest` scans those source files directly. Computed once, lazily,
 * and reused across every `[Name]ScreenContent` checked by that rule.
 */
private val snapshotTestSources: List<String> by lazy {
    Konsist
        .scopeFromProject()
        .files
        .filter { it.path.contains("/src/androidHostTest/") }
        .map { it.text }
}
