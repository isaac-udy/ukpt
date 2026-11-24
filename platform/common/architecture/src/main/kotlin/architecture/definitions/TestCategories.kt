package architecture.definitions

import dev.isaacudy.udytils.architecture.TestCategory

/** The test categories of `:platform:common:testing`; each id is the category's JUnit tag. */
object TestCategories {
    val Unit = TestCategory("unit", "Unit")
    val Component = TestCategory("component", "Component")
    val Snapshot = TestCategory("snapshot", "Snapshot")
    val Subcutaneous = TestCategory("subcutaneous", "Subcutaneous")
    val Wiring = TestCategory("wiring", "Wiring")
    val Adapter = TestCategory("adapter", "Adapter")
    val Check = TestCategory("check", "Check")
    val EndToEnd = TestCategory("end-to-end", "End-to-end")
}
