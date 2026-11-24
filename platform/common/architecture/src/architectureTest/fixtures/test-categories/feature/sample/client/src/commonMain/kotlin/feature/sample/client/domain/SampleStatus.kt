package feature.sample.client.domain

sealed interface SampleStatus {
    data object Idle : SampleStatus
    data class Failed(val reason: String) : SampleStatus
}
