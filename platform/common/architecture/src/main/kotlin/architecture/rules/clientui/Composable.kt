package architecture.rules.clientui

import dev.isaacudy.udytils.architecture.*

import architecture.definitions.isFeatureModule
import com.lemonappdev.konsist.api.Konsist

@Describe("""
    A `@Composable` function defined in the `..ui..` package that is not a [Screen](#screen).
    Typically a sub-component used by one or more screens, an inline editor, a feature-specific
    overlay, or a `@Preview` function.

    * **Note:** `[Name]ScreenContent` companions (see `ClientUi.Screen.screenContentCompanion`)
      are non-Screen composables, which is why the snapshot rules live on this Construct.
      For reusable design-system primitives (buttons, fields), prefer a shared composable in
      `:platform:client:design`. Feature-local composables live alongside the Screen they support.

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
    * **Note:** A screen's golden should read as a **screenshot of the app on a device**, not a
      render on the harness canvas: wrap the preview's content in the design module's
      `UkptPreviewFrame`, which sizes the render to the project's primary viewport and pins the
      palette. The module's `PreviewSnapshotTest` renders in `RenderingMode.SHRINK`, cropping the
      golden to that frame — the 960 dp canvas is a ceiling, not a frame. (An unframed
      `fillMaxSize` preview still renders the full square canvas, unchanged.)
    * **Note:** For one-off snapshot tests that aren't preview-driven, `SnapshotRule`
      (`dev.isaacudy.udytils.snapshot.SnapshotRule`) provides `snapshot.screen { }` and
      `snapshot.component { }`.
    * **Note:** Record golden images after adding or changing a preview, then verify they match
      (goldens are committed under `src/androidHostTest/snapshots/images/`). Both tasks need
      `--no-configuration-cache` (under the cache the R class is dropped from the test classpath —
      see the configuration-cache migration):

      ```
      ./gradlew :feature:core:client:recordPaparazzi --no-configuration-cache
      ./gradlew :feature:core:client:verifyPaparazzi --no-configuration-cache
      ```
""")
object Composable : Construct<ClientUi>(
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
                .filter { it.resideInPackage("feature..client.ui..") }
                .filterNot { exempt(it) }
                .filterNot { fn ->
                    previewsByFile[fn.containingFile.path]
                        .orEmpty()
                        .any { preview -> preview.text.contains("${fn.name}(") }
                }
                .map { Violation(it, "no @Preview composable in the same file calls `${it.name}(`") }
        }
    }

    @Describe("Dialog primitives (`AlertDialog`, `BasicAlertDialog`, `DatePickerDialog`, `ModalBottomSheet`, `androidx.compose.ui.window.Dialog`) may only be invoked in a file that declares a dialog destination (one containing a `directOverlay` metadata marker)")
    val dialogPrimitivesOnlyInDialogDestinations by rule {
        rationale(
            """
            Dialogs are their own destinations: a `NavigationKey.WithResult<R>` rendered through
            Enro's overlay support, not an inline composable toggled by a boolean in screen state.
            Restricting dialog primitives to dialog-destination files makes embedded dialogs a build
            failure, not a review finding. Platform and design-system modules are exempt — they may
            define dialog primitives and wrappers.
            """.trimIndent(),
        )
        note("Detection is import-based: an import of any dialog primitive in a non-dialog-destination file is a violation, regardless of whether the call site is reached.")
        val dialogPrimitiveImports = listOf(
            "androidx.compose.material3.AlertDialog",
            "androidx.compose.material3.BasicAlertDialog",
            "androidx.compose.material3.DatePickerDialog",
            "androidx.compose.material3.ModalBottomSheet",
            "androidx.compose.ui.window.Dialog",
        )
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() && it.packagee?.name?.contains(".client.ui") == true }
                .filterNot { exempt(it) }
                .filter { file ->
                    file.imports.any { import -> dialogPrimitiveImports.any { import.name.startsWith(it) } }
                }
                .filterNot { file -> file.text.contains("directOverlay") }
                .map { Violation(it.path, "dialog primitive outside a dialog destination — dialogs are their own destinations (see ClientUi guidance)") }
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
        note("A module opts in by extending `PreviewSnapshotTestCase` from `dev.isaacudy.udytils:snapshot`, which supplies the preview scanning and the golden layout.")
        scope { scope, exempt ->
            scope.functions()
                .filter { it.hasAnnotationWithName("Preview") }
                .filter { it.resideInPackage("feature..") }
                .filterNot { exempt(it) }
                .groupBy { it.containingFile.path.substringBefore("/src/") }
                .filterNot { (module, _) ->
                    hostTestFiles.any { (path, text) ->
                        path.startsWith("$module/src/androidHostTest/") &&
                            // Match the harness base class, not the scanner it wraps: the scanner is an
                            // implementation detail of `dev.isaacudy.udytils:snapshot` and no longer
                            // appears in consumer code, while every preview-driven test must extend
                            // this class regardless of whether it sources cases via `scan` or `of`.
                            text.contains("PreviewSnapshotTestCase")
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
