package architecture.rules.serverweb

import dev.isaacudy.udytils.architecture.*

import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration

@Describe("""
    A top-level function that computes a value for rendering and has no kotlinx.html receiver: a
    formatter, or a mapper such as `GreetingSummary.toPageState()`.
""")
object ViewHelper : Construct<ServerWeb>(
    requirements = listOf(
        isFunctionWhere("returns a value, has no kotlinx.html receiver, and is not an Event Stream") { fn ->
            val receiver = fn.receiverType?.name
            val htmlReceiver = receiver != null && fn.containingFile.imports.any { it.name == "kotlinx.html.$receiver" }
            val returnsValue = fn.returnType != null && fn.returnType?.name != "Unit"
            !htmlReceiver && returnsValue && !fn.isEventStream()
        },
    ),
) {
    @Describe("A View Helper must not suspend")
    val notSuspending by rule {
        rationale("A View Helper runs while a page renders, and rendering does not suspend; reading from the domain is the handler's job.")
        constrain { decl, _ ->
            val fn = decl as? KoFunctionDeclaration ?: return@constrain emptyList()
            if (fn.hasSuspendModifier) listOf(Violation(fn, "View Helper `${fn.name}` is `suspend`")) else emptyList()
        }
    }
}
