package architecture.rules.testing

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import dev.isaacudy.udytils.architecture.*

@Describe("""
    A `@SnapshotTest` class: renders previews with Paparazzi and compares each with its committed
    golden image. A module's `PreviewSnapshotTest` extends `PreviewSnapshotTestCase` from
    `dev.isaacudy.udytils:snapshot`, which renders every `@Preview` in the package tree it scans.

    ```
    ./gradlew :feature:core:client:recordPaparazzi --no-configuration-cache
    ./gradlew snapshotTest --no-configuration-cache
    ```
""")
object SnapshotTest : Construct<Testing>(
    requirements = listOf(isClass, isAnnotatedWith("SnapshotTest")),
) {
    @Describe("A Snapshot Test must live in the `androidHostTest` source set")
    val placement by rule {
        rationale("Paparazzi runs as an Android host test.")
        constrain(placement("`androidHostTest`") { it.sourceSet() == "androidHostTest" })
    }

    @Describe("A Snapshot Test must extend `PreviewSnapshotTestCase` or use `SnapshotRule`")
    val usesTheSnapshotHarness by rule {
        rationale("The harness owns the golden layout and the device and rendering defaults.")
        constrain { decl, _ ->
            val extendsHarness = (decl as KoClassDeclaration).parents().any { it.name == "PreviewSnapshotTestCase" }
            if (extendsHarness || "SnapshotRule" in decl.fileCode()) emptyList()
            else listOf(Violation(decl, "neither extends `PreviewSnapshotTestCase` nor uses `SnapshotRule`"))
        }
    }
}
