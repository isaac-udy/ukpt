package platform.server.web

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/** The outcome of validating a form: the typed [Valid.value], or the form's [Invalid.errors]. */
sealed interface FormResult<out T, out E> {
    data class Valid<T>(val value: T) : FormResult<T, Nothing>
    data class Invalid<E>(val errors: E) : FormResult<Nothing, E>
}

/** The redirect that ends a post/redirect/get: the browser follows it with a `GET`. */
suspend fun ApplicationCall.respondSeeOther(url: String) {
    response.headers.append(HttpHeaders.Location, url)
    respond(HttpStatusCode.SeeOther)
}
