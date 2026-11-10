package feature.sample.server.web

internal data class SamplePageState(
    val labels: List<String>,
    val form: SampleForm,
    val errors: SampleForm.Errors?,
)
