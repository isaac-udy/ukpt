package architecture.rules.testing

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A function with a `ServerInspection` receiver, in a `:feature:[name]:tests` module's
    `src/commonMain`: reads the server's state for a test to assert on. A test calls it inside
    `inspectServer { }`, which retries the block until the block passes or 10 seconds of real
    time pass.

    ```kotlin
    suspend fun ServerInspection.currentGreetingHistory(): List<Greeting> = server<FlowOfGreetingHistory>()().first()

    suspend fun ServerInspection.storedGreetingTexts(): List<String> = database {
        GreetingsTable.selectAll().map { it[GreetingsTable.text] }
    }
    ```

    `server<T>()` resolves a server domain read interface, or a Fake bound in
    `edges = { server { … } }`. `database { }` runs a read-only transaction on the test's
    database, for a stored fact the server domain does not expose.
""")
object ServerInspection : Construct<Testing>(
    requirements = listOf(hasServerInspectionReceiver),
) {
    @Describe("A Server Inspection must live in a `:feature:[name]:tests` module's `src/commonMain`")
    val placement by rule {
        rationale("Tests in the same module call the Inspection, and another feature's `:tests` module depends on this one to reuse it.")
        constrain(inspectionPlacement())
    }

    @Describe("A Server Inspection must only read: server domain interfaces named for a read (`Get…`, `FlowOf…`, `List…`, `Find…`, …), domain models, generated tables through `database { }`, and Exposed queries; no Exposed writes, no SQL, no other server class")
    val readsOnly by rule {
        rationale(
            """
            A test asserts on the state the code under test produced. An Inspection that writes
            changes that state, and the assertion then covers a state the application did not
            produce.
            """.trimIndent(),
        )
        note("`database { }` also runs a read-only transaction, so Postgres rejects a write that the rule does not detect.")
        constrainWithScopes { decl, scopes, _ ->
            readsOnlyViolations(decl, scopes, layer = "ServerDomain") +
                exposedWriteViolations(decl) +
                noImportsOf(DIRECT_DATABASE_IMPORTS).run(decl) { false }
        }
    }
}
