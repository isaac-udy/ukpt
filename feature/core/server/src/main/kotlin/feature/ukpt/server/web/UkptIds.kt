package feature.ukpt.server.web

import dev.isaacudy.udytils.htmx.ElementId

internal object UkptIds {
    val greetingForm = ElementId("greeting-form")
    val greetings = ElementId("greetings")

    const val GREETINGS_EVENT = "greetings"

    fun greeting(id: Long): ElementId = ElementId("greeting-$id")
}
