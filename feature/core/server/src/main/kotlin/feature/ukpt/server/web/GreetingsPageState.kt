package feature.ukpt.server.web

import feature.ukpt.Greeting

internal data class GreetingsPageState(
    val greetings: List<Greeting>,
    val form: GreetingForm,
    val errors: GreetingForm.Errors?,
)
