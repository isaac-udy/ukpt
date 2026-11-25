package ukpt

import org.gradle.api.tasks.CacheableTask
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

/** A JVM-target test task in a multiplatform module that Kover instruments like `jvmTest`. */
@CacheableTask
abstract class JvmCategoryTest : KotlinJvmTest() {
    init {
        targetName = "jvm"
    }
}
