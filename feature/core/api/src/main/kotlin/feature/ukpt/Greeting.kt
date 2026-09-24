package feature.ukpt

import kotlinx.serialization.Serializable

/**
 * A worked-example domain object: an immutable, `@Serializable` value. Copy this shape for real
 * domain data. Part of the `feature.ukpt` example slice that the architecture rules describe.
 */
@Serializable
data class Greeting(
    val text: String,
)
