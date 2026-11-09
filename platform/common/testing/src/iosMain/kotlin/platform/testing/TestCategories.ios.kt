package platform.testing

@Target(AnnotationTarget.CLASS)
actual annotation class UnitTest

@Target(AnnotationTarget.CLASS)
actual annotation class SubcutaneousTest

@Target(AnnotationTarget.CLASS)
actual annotation class ComponentTest

@Target(AnnotationTarget.CLASS)
actual annotation class SnapshotTest

@Target(AnnotationTarget.CLASS)
actual annotation class WiringTest

@Target(AnnotationTarget.CLASS)
actual annotation class AdapterTest

@Target(AnnotationTarget.CLASS)
actual annotation class CheckTest

@Target(AnnotationTarget.CLASS)
actual annotation class EndToEndTest
