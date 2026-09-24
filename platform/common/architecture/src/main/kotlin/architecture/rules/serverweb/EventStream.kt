package architecture.rules.serverweb

import dev.isaacudy.udytils.architecture.*

import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration

@Describe("""
    The server-sent events a page subscribes to: a top-level function named `[name]Events` that
    returns `Flow<ServerSentEvent>`, built from a domain `Flow`. A handler sends it from an `sse(…)`
    route, and the page connects with the udytils `sse { connect(…) }` attributes.

    For a list, `dev.isaacudy.udytils.htmx.OobList` turns each version of the list into
    out-of-band swaps rendered by the same [Component](#component) the page uses for each item.
""")
object EventStream : Construct<ServerWeb>(
    requirements = listOf(
        isFunctionWhere("is named `[name]Events` and returns `Flow<ServerSentEvent>`") { it.isEventStream() },
    ),
) {
    @Describe("An Event Stream should send the whole of what it keeps current as its first event, then only the changes")
    val snapshotFirst by guidance {
        note("A page is rendered before its stream connects, and a stream reconnects after a dropped connection; the first event brings the page up to date in both cases. `OobList` does this through `oobUpdates`.")
    }
}

internal fun KoFunctionDeclaration.isEventStream(): Boolean {
    val returnType = returnType?.name?.replace(" ", "") ?: return false
    return name.endsWith("Events") && returnType.startsWith("Flow<") && "ServerSentEvent" in returnType
}
