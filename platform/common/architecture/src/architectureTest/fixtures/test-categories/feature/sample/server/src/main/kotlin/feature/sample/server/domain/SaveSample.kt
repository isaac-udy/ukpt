package feature.sample.server.domain

fun interface SaveSample {
    suspend operator fun invoke(label: String)
}
