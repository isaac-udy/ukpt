package platform.testing

/** One production class exercised over many inputs; collaborators are test fakes or lambdas. */
@Target(AnnotationTarget.CLASS)
expect annotation class UnitTest()

/** A real ViewModel or service from the production graph, a real in-process server and database. */
@Target(AnnotationTarget.CLASS)
expect annotation class SubcutaneousTest()

/** Renders one screen's content with fixed state and drives it. */
@Target(AnnotationTarget.CLASS)
expect annotation class ComponentTest()

/** Renders previews and compares them with golden images. */
@Target(AnnotationTarget.CLASS)
expect annotation class SnapshotTest()

/** Resolves a dependency-injection graph. */
@Target(AnnotationTarget.CLASS)
expect annotation class WiringTest()

/** One infrastructure adapter against the real infrastructure. */
@Target(AnnotationTarget.CLASS)
expect annotation class AdapterTest()

/** Two committed artifacts agree. */
@Target(AnnotationTarget.CLASS)
expect annotation class CheckTest()

/** A real application shell driven through its UI against an in-process server. */
@Target(AnnotationTarget.CLASS)
expect annotation class EndToEndTest()
