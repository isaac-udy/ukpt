package platform.network

import dev.isaacudy.udytils.trace.TraceContext
import dev.isaacudy.udytils.trace.traceparent
import dev.isaacudy.udytils.urpc.UrpcClientInterceptor

/** Sends the caller's span as the call's W3C `traceparent`, so the server's spans join the caller's trace. */
internal val traceparentInterceptor = UrpcClientInterceptor { context ->
    val span = context.callerContext[TraceContext]?.span ?: return@UrpcClientInterceptor
    context.metadata["traceparent"] = span.traceparent
}
