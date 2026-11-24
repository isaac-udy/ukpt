package ukpt

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.testing.Test

/** An Android host test task in a multiplatform module that Kover instruments like `testAndroidHostTest`. */
abstract class AndroidCategoryTest : AndroidUnitTest()

/** Kover binds a KMP Android host task only when a superclass has this simple name and the task declares [variantName]. */
abstract class AndroidUnitTest : Test() {
    @get:Internal
    val variantName: String = "androidHostTest"
}
