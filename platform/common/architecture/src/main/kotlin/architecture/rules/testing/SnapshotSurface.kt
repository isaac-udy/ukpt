package architecture.rules.testing

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A `@Composable` function in `androidHostTest`: a preview that exists only to be snapshotted,
    such as a design-system doc surface, and the frame it renders in.
""")
object SnapshotSurface : Construct<Testing>(
    requirements = listOf(
        isFunction,
        isAnnotatedWith("Composable"),
        predicate("lives in `androidHostTest`") { it.sourceSet() == "androidHostTest" },
    ),
)
