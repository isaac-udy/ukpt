package feature.ukpt.server.web

import dev.isaacudy.udytils.htmx.ElementId

object UkptIds {
    val greetingForm = ElementId("greeting-form")
    val greetings = ElementId("greetings")

    const val GREETINGS_EVENT = "greetings"

    fun greeting(id: String): ElementId = ElementId("greeting-$id")
}
