package feature.sample.client.domain

fun interface SampleSource {
    operator fun invoke(): String
}
