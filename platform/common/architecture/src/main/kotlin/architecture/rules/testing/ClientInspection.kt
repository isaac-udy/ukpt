package architecture.rules.testing

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A function with a `ClientInspection` receiver, in a `:feature:[name]:tests` module's
    `src/commonMain`: reads the client's state for a test to assert on, such as the calls a Fake
    bound in `edges = { client { … } }` received. A test calls it inside `inspectClient { }`,
    which retries the block until the block passes or 10 seconds of real time pass.

    ```kotlin
    fun ClientInspection.paymentRequests(): List<PaymentRequest> = client<FakePaymentGateway>().requests.calls
    ```

    `client<T>()` resolves a client domain read interface or a Fake bound at a client edge. A Fake
    records its calls in a `Recording`.
""")
object ClientInspection : Construct<Testing>(
    requirements = listOf(hasClientInspectionReceiver),
) {
    @Describe("A Client Inspection must live in a `:feature:[name]:tests` module's `src/commonMain`")
    val placement by rule {
        rationale("Tests in the same module call the Inspection, and another feature's `:tests` module depends on this one to reuse it.")
        constrain(inspectionPlacement())
    }

    @Describe("A Client Inspection must only read: client domain interfaces named for a read (`Get…`, `FlowOf…`, `List…`, `Find…`, …), domain models, and Fakes; no other client class")
    val readsOnly by rule {
        rationale(
            """
            A test asserts on the state the code under test produced. An Inspection that writes
            changes that state, and the assertion then covers a state the application did not
            produce.
            """.trimIndent(),
        )
        constrainWithScopes { decl, scopes, _ -> readsOnlyViolations(decl, scopes, layer = "ClientDomain") }
    }
}
