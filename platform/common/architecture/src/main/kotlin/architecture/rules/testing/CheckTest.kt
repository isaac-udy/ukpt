package architecture.rules.testing

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A `@CheckTest` class: checks that two committed artifacts agree, such as a stylesheet's tokens
    and the Kotlin constants that name them. It tests the project, not application behaviour.
""")
object CheckTest : Construct<Testing>(
    requirements = listOf(isClass, isAnnotatedWith("CheckTest")),
) {
    @Describe("A Check Test must live in a `src/test` source set")
    val placement by rule {
        rationale("A check reads files from disk, which the JVM test tasks can do.")
        constrain(placement("`src/test`") { it.sourceSet() == "test" })
    }
}
