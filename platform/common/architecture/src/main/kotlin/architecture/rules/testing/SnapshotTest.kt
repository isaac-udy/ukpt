package architecture.rules.testing

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A `@SnapshotTest` class: renders pages with fixed View States and compares each with its
    committed golden HTML, through `HtmlSnapshots` from `dev.isaacudy.udytils:html-snapshot`. A
    module applies `ukpt.html-snapshot`; goldens live in `src/test/snapshots/html`.

    ```
    ./gradlew :feature:core:server:snapshotTest -PrecordHtmlSnapshots
    ./gradlew snapshotTest
    ```
""")
object SnapshotTest : Construct<Testing>(
    requirements = listOf(isClass, isAnnotatedWith("SnapshotTest")),
) {
    @Describe("A Snapshot Test must live in the `src/test` source set of a `:feature:[name]:server` module or `:platform:server:web`")
    val placement by rule {
        rationale("`ukpt.html-snapshot` keeps the goldens beside the module's tests, and the pages it renders are `internal` to the module.")
        constrain(
            placement("a `:feature:[name]:server` module's or `:platform:server:web`'s `src/test/`") { decl ->
                decl.sourceSet() == "test" && decl.isInServerWebModule()
            },
        )
    }

    @Describe("A Snapshot Test must compare through `HtmlSnapshots`")
    val usesTheSnapshotHarness by rule {
        rationale("The harness normalises the markup before comparing, and `-PrecordHtmlSnapshots` rewrites its goldens.")
        constrain { decl, _ ->
            if ("HtmlSnapshots" in decl.fileCode()) emptyList()
            else listOf(Violation(decl, "does not use `HtmlSnapshots`"))
        }
    }
}
