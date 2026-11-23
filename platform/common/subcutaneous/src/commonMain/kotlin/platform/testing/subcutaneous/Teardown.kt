package platform.testing.subcutaneous

/** Runs the registered steps in reverse; a step's failure never skips the steps after it. */
class Teardown {
    private val steps = ArrayDeque<suspend () -> Unit>()

    operator fun invoke(step: suspend () -> Unit) {
        steps.addFirst(step)
    }

    suspend fun runAll(primary: Throwable?) {
        var failure = primary
        while (steps.isNotEmpty()) {
            try {
                steps.removeFirst().invoke()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        if (primary == null && failure != null) throw failure
    }
}

/** Runs [block] with a [Teardown], then every step it registered, whether [block] returned or threw. */
suspend fun <T> withTeardown(block: suspend (Teardown) -> T): T {
    val teardown = Teardown()
    val result = try {
        block(teardown)
    } catch (failure: Throwable) {
        teardown.runAll(failure)
        throw failure
    }
    teardown.runAll(null)
    return result
}
