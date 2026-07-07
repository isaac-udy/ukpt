package feature.ukpt.domain

import kotlinx.coroutines.flow.Flow

/**
 * A worked-example `FlowOf…` domain interface returning a nullable-element stream,
 * `Flow<Greeting?>` — exercises a nullable domain type inside the reactive wrapper. Implemented by
 * `FlowOfLatestGreetingImpl` in `:client`.
 */
fun interface FlowOfLatestGreeting {
    operator fun invoke(): Flow<Greeting?>
}
