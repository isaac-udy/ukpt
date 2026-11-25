package ukpt

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Tags
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class TestTagsTest {
    @Tag("component")
    private annotation class Component

    @Component
    private interface ComponentContract

    private open class Parent : ComponentContract {
        @Tags(Tag("unit"), Tag("adapter"))
        fun taggedMethod() = Unit
    }

    private class Child : Parent()

    @Test
    fun followsParentMethodsInterfacesAndComposedAnnotationsOutsideTestClasses() {
        val child = Child::class.java
        val classes = File(child.protectionDomain.codeSource.location.toURI())
        val classFile = classes.resolve(child.name.replace('.', '/') + ".class")
        assertEquals(setOf("component", "unit", "adapter"), TestTags(child.classLoader).read(setOf(classFile)))
    }
}
