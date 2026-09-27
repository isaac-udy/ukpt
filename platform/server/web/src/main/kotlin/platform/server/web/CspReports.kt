package platform.server.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.io.readString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Where browsers send the policy's violations: `report-uri` names it, and `report-to` names [CSP_REPORT_GROUP]. */
const val CSP_REPORT_PATH: String = "/csp-report"

internal const val CSP_REPORT_GROUP: String = "csp"

private const val MAX_REPORT_BYTES = 16 * 1024L
private const val MAX_VIOLATIONS_PER_MINUTE = 60
private const val MAX_FIELD_LENGTH = 300

/**
 * Logs each violation a browser reports as a warning, so a report-only policy can be checked
 * against real traffic before it is enforced. Anyone can post here: a report is read up to
 * [MAX_REPORT_BYTES], its URLs lose their query strings, and at most [MAX_VIOLATIONS_PER_MINUTE]
 * are logged a minute.
 */
internal fun Route.cspReports() {
    val limit = MinuteLimit(MAX_VIOLATIONS_PER_MINUTE)
    post(CSP_REPORT_PATH) {
        val body = call.receiveChannel().readRemaining(MAX_REPORT_BYTES).readString()
        for (violation in parseCspReport(body)) {
            val n = limit.next()
            when {
                n <= MAX_VIOLATIONS_PER_MINUTE -> call.application.log.warn(violation.describe())
                n == MAX_VIOLATIONS_PER_MINUTE + 1 ->
                    call.application.log.warn("CSP violations: more than $MAX_VIOLATIONS_PER_MINUTE this minute, the rest are not logged")
            }
        }
        call.respond(HttpStatusCode.NoContent)
    }
}

internal data class CspViolation(
    val directive: String?,
    val blocked: String?,
    val document: String?,
    val source: String?,
    val line: Int?,
    val disposition: String?,
) {
    fun describe(): String = buildString {
        append("CSP violation (${disposition ?: "unknown"}): ${directive ?: "?"} blocked ${blocked ?: "?"} on ${document ?: "?"}")
        if (source != null) append(" from $source${line?.let { ":$it" }.orEmpty()}")
    }
}

/**
 * Reads both report formats: the Reporting API's batch (`report-to`, `application/reports+json`)
 * and the older single `{"csp-report": …}` (`report-uri`, `application/csp-report`).
 */
internal fun parseCspReport(body: String): List<CspViolation> =
    when (val json = runCatching { Json.parseToJsonElement(body) }.getOrNull()) {
        is JsonArray -> json.mapNotNull { report ->
            val entry = report as? JsonObject ?: return@mapNotNull null
            if (entry.text("type") != "csp-violation") return@mapNotNull null
            val b = entry["body"] as? JsonObject ?: return@mapNotNull null
            CspViolation(
                directive = b.text("effectiveDirective"),
                blocked = b.url("blockedURL"),
                document = b.url("documentURL"),
                source = b.url("sourceFile"),
                line = b.number("lineNumber"),
                disposition = b.text("disposition"),
            )
        }
        is JsonObject -> listOfNotNull(
            (json["csp-report"] as? JsonObject)?.let { r ->
                CspViolation(
                    directive = r.text("effective-directive") ?: r.text("violated-directive"),
                    blocked = r.url("blocked-uri"),
                    document = r.url("document-uri"),
                    source = r.url("source-file"),
                    line = r.number("line-number"),
                    disposition = r.text("disposition"),
                )
            },
        )
        else -> emptyList()
    }

private fun JsonObject.primitive(key: String): JsonPrimitive? = get(key) as? JsonPrimitive

private fun JsonObject.text(key: String): String? =
    primitive(key)?.takeIf { it.isString }?.content
        ?.filterNot { it.isISOControl() }
        ?.take(MAX_FIELD_LENGTH)
        ?.takeIf { it.isNotBlank() }

/** A query string can carry a token or someone's details, so a logged URL stops at its path. */
private fun JsonObject.url(key: String): String? = text(key)?.substringBefore('?')?.substringBefore('#')

private fun JsonObject.number(key: String): Int? = primitive(key)?.intOrNull

private class MinuteLimit(private val perMinute: Int) {
    private var windowStart = 0L
    private var count = 0

    @Synchronized
    fun next(): Int {
        val now = System.currentTimeMillis()
        if (now - windowStart >= 60_000) {
            windowStart = now
            count = 0
        }
        if (count <= perMinute + 1) count++
        return count
    }
}
