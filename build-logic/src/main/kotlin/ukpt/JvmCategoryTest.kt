package ukpt

import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

/** A JVM-target test task in a multiplatform module that Kover instruments like `jvmTest`. */
abstract class JvmCategoryTest : KotlinJvmTest() {
    init {
        targetName = "jvm"
    }
}
