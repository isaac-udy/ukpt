package architecture.rules.ui

import dev.isaacudy.udytils.architecture.*

import com.lemonappdev.konsist.api.Konsist

@Describe("""
    A `@Composable` function defined in the `..ui..` package that is not a [Screen](#screen).
    Typically a sub-component used by one or more screens, an inline editor, a feature-specific
    overlay, or a `@Preview` function.

    * **Note:** `[Name]ScreenContent` companions (see `UiLayer.Screen.screenContentCompanion`)
      are non-Screen composables, which is why the snapshot rules live on this Construct.
      For reusable design-system primitives (buttons, fields), prefer a shared composable in
      `:platform:client:ui`. Feature-local composables live alongside the Screen they support.

    ### Snapshot tests

    Snapshots are preview-driven: `PreviewSnapshotTest` (in `src/androidHostTest/`) discovers
    every `@Preview` composable in the module from the compiled classes
    (ComposablePreviewScanner) and renders each one with
    [Paparazzi](https://github.com/cashapp/paparazzi), recording a golden image that catches
    visual regressions without a device or emulator. Adding a `@Preview` to a composable is all
    that is needed to snapshot it.

    * **Note:** Use the unified `@Preview` (`androidx.compose.ui.tooling.preview.Preview`)
      directly in common code; `compose.preview` must be a `commonMain` dependency. The same
      previews render in the IDE.
    * **Note:** Add a `@Preview` per meaningful state (loaded, empty, error) as a screen grows.
    * **Note:** A screen's `@Preview`(s) live in the same file as the `[Name]ScreenContent` they
      render, next to the Screen — not gathered into a shared "screen previews" file.
    * **Note:** For one-off snapshot tests that aren't preview-driven, `SnapshotRule`
      (`platform.snapshot.SnapshotRule`) provides `snapshot.screen { }` and
      `snapshot.component { }`.
    * **Note:** Record golden images after adding or changing a preview, then verify they match
      (goldens are committed under `src/androidHostTest/snapshots/images/`):

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
    @Describe("A `[Name]ScreenContent` composable must be called from a `@Preview` composable in the same file")
    val screenContentPreview by rule {
        rationale(
            """
            Previews are the snapshot surface: `PreviewSnapshotTest` renders every `@Preview` in
            the module, so a ScreenContent without a preview has no snapshot coverage. The preview
            must live in the same file as the ScreenContent it renders — co-locating it keeps each
            screen's preview next to the screen, discoverable and maintained with it, instead of
            drifting into a single shared "screen previews" file.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            val previewsByFile = scope.functions()
                .filter { it.hasAnnotationWithName("Preview") }
                .groupBy { it.containingFile.path }
            scope.functions()
                .filter { it.name.endsWith("ScreenContent") }
                .filter { it.resideInPackage("feature..ui..") }
                .filterNot { exempt(it) }
                .filterNot { fn ->
                    previewsByFile[fn.containingFile.path]
                        .orEmpty()
                        .any { preview -> preview.text.contains("${fn.name}(") }
                }
                .map { Violation(it, "no @Preview composable in the same file calls `${it.name}(`") }
        }
    }

    @Describe("A feature module that contains `@Preview` composables must have a `PreviewSnapshotTest` in its `androidHostTest` source set")
    val previewsAreSnapshotTested by rule {
        rationale(
            """
            The scanner test is what turns previews into snapshots; without it, previews render in
            the IDE but nothing guards against visual regressions.
            """.trimIndent(),
        )
        note("Snapshot tests live under `src/androidHostTest/`, which the governed scope excludes; the test reads those files directly.")
        scope { scope, exempt ->
            scope.functions()
                .filter { it.hasAnnotationWithName("Preview") }
                .filter { it.resideInPackage("feature..") }
                .filterNot { exempt(it) }
                .groupBy { it.containingFile.path.substringBefore("/src/") }
                .filterNot { (module, _) ->
                    hostTestFiles.any { (path, text) ->
                        path.startsWith("$module/src/androidHostTest/") &&
                            text.contains("AndroidComposablePreviewScanner")
                    }
                }
                .map { (module, previews) ->
                    Violation(previews.first(), "module `$module` has @Preview composables but no PreviewSnapshotTest in androidHostTest")
                }
        }
    }
}

/**
 * Snapshot tests live under `src/androidHostTest/`, which `projectScope` deliberately excludes, so
 * `Composable.previewsAreSnapshotTested` reads those source files directly. Computed once, lazily.
 */
private val hostTestFiles: List<Pair<String, String>> by lazy {
    Konsist
        .scopeFromProject()
        .files
        .filter { it.path.contains("/src/androidHostTest/") }
        .map { it.path to it.text }
}
