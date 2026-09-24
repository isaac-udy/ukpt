package architecture.rules.serverweb

import dev.isaacudy.udytils.architecture.*

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

@Describe("""
    The fields of one HTML form as the browser submitted them: a `data class [Name]Form` whose
    properties are raw input, read with `[Name]Form.from(call.receiveParameters())`. Its
    `validate()` returns a `platform.server.web.FormResult`: the typed value the handler passes to
    the domain, or the form's `Errors`.

    A handler re-renders an invalid form from the same `[Name]Form` and its `Errors`, so the page
    shows exactly what was submitted.
""")
object Form : Construct<ServerWeb>(
    requirements = listOf(
        isDataClass,
        hasNameEndingWith("Form"),
    ),
) {
    @Describe("A Form's constructor properties must be raw input: `String`, `String?`, `Boolean`, or `List<String>`")
    val rawInputProperties by rule {
        rationale("A property of any other type is a conversion that can fail before `validate()` runs, where the failure has no field to report against.")
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            cls.primaryConstructor?.parameters.orEmpty()
                .filter { it.type.name.replace(" ", "") !in rawInputTypes }
                .map { Violation(cls, "Form property `${it.name}` is `${it.type.name}`, not raw input") }
        }
    }

    @Describe("A Form must declare `validate()`")
    val declaresValidate by rule {
        constrain { decl, _ ->
            val cls = decl as? KoClassDeclaration ?: return@constrain emptyList()
            if (cls.functions().any { it.name == "validate" }) emptyList() else listOf(Violation(cls, "`${cls.name}` has no `validate()`"))
        }
    }
}

private val rawInputTypes = setOf("String", "String?", "Boolean", "List<String>")
