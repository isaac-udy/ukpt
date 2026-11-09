package architecture.rules.testing

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A `@CheckTest` class: checks that two committed artifacts agree, such as the Compose HTML
    stylesheet's tokens and the Compose design tokens (`UkptDesignCssTest`), or the design-system
    pages and the goldens they embed (`DesignSystemDocImagesTest`). It tests the project, not
    application behaviour.
""")
object CheckTest : Construct<Testing>(
    requirements = listOf(isClass, isAnnotatedWith("CheckTest")),
) {
    @Describe("A Check Test must live in a JVM test source set: `jvmTest`, `desktopTest` or `src/test`")
    val placement by rule {
        rationale("A check reads files from disk, which the JVM test tasks can do.")
        constrain(placement("`jvmTest`, `desktopTest` or `src/test`") { it.sourceSet() in setOf("jvmTest", "desktopTest", "test") })
    }
}
