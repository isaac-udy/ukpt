package architecture.rules.testing

import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import dev.isaacudy.udytils.architecture.*

internal val hasScenarioReceiver = isFunctionWhere("has a `Scenario` receiver") { it.receiverType?.name == "Scenario" }

@Describe("""
    A function with a `Scenario` receiver, in a `:feature:[name]:tests` module's `src/main`: puts
    the test database into a state through the feature's server domain interfaces. A test calls it
    inside `given { }`, before it opens a ViewModel or while one is open; it returns what it created.

    ```kotlin
    suspend fun Scenario.greetingHistory(vararg texts: String): List<Greeting> {
        val updateGreetingHistory = server<UpdateGreetingHistory>()
        texts.forEach { updateGreetingHistory.add(it) }
        return server<FlowOfGreetingHistory>()().first()
    }
    ```

    Another feature's `:tests` module depends on this one to reuse its scenarios.
""")
object Scenario : Construct<Testing>(
    requirements = listOf(hasScenarioReceiver),
) {
    @Describe("A Scenario must write through server domain interfaces: no server data classes, generated tables, Exposed, JDBC or SQL")
    val seedsThroughServerDomain by rule {
        rationale(
            """
            A row written past the domain skips the invariants the server enforces, and the test
            then covers a state the application cannot produce. A Scenario that calls the domain also
            stays valid when the storage below it changes.
            """.trimIndent(),
        )
        constrainWithScopes { decl, scopes, _ ->
            val data = scopes.productionReferences(decl.file)
                .filter { reference -> reference.constructs.any { it.id.startsWith("ServerData.") } }
                .map { Violation(decl, "references `${it.name}` (${it.constructs.joinToString { c -> c.id }})") }
            val sql = if (SQL.containsMatchIn((decl as KoFunctionDeclaration).text)) listOf(Violation(decl, "contains SQL")) else emptyList()
            data + sql + noImportsOf(STORAGE_IMPORTS).run(decl) { false }
        }
    }

    @Describe("A Scenario should return what it created, such as the seeded domain models")
    val returnsHandles by guidance {
        rationale("The test refers to seeded records by the returned values instead of reading them back through the screen.")
        audit { decl, _ ->
            val function = decl as KoFunctionDeclaration
            if (function.returnType == null || function.returnType?.name == "Unit") {
                listOf(Violation(decl, "`${function.name}` returns nothing"))
            } else {
                emptyList()
            }
        }
    }
}

private val SQL = Regex("""\b(INSERT\s+INTO|DELETE\s+FROM|UPDATE\s+\w+\s+SET|SELECT\s+.+?\s+FROM|TRUNCATE)\b""")

private val STORAGE_IMPORTS = listOf(
    "org.jetbrains.exposed.",
    "java.sql.",
    "javax.sql.",
    "platform.server.postgres.tables.",
    "dev.isaacudy.udytils.postgres.",
)
