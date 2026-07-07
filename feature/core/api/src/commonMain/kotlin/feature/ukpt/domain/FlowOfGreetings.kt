package feature.ukpt.domain

import kotlinx.coroutines.flow.Flow

/**
 * A worked-example `FlowOf…` domain interface: its primary function returns `Flow<Greeting>`, so it
 * carries the `FlowOf` name prefix. Implemented by `FlowOfGreetingsImpl` in `:client`.
 */
fun interface FlowOfGreetings {
    operator fun invoke(): Flow<Greeting>
}
