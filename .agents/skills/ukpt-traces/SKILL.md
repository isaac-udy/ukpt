---
name: ukpt-traces
description: >-
  Record and read the app's traces in a local Jaeger — start the viewer, run the desktop app and
  the server with trace export, list recent traces, and print one trace as a tree across client
  and server, with links to screen visits and spans whose parent Jaeger does not store. Use when
  checking what a user action calls end to end, where a request spends its time, which call
  failed, or whether trace propagation works.
---

# ukpt-traces

The trace compiler plugin records spans in the `:feature` modules. The desktop app and the
server export the spans over OTLP/HTTP only when the `UKPT_TRACE_EXPORT` environment variable is
set to a collector's address. The root build's `traceViewerStart` task runs a local Jaeger v2,
which stores the spans in memory.

## Record

1. Start Jaeger: `./gradlew traceViewerStart`. The UI is on http://localhost:16686 and OTLP/HTTP
   on http://localhost:4318. The first run downloads Jaeger and checks the release checksum.
2. Start the server and the app with export (see `ukpt-run` for the dev database):
   ```
   UKPT_TRACE_EXPORT=http://localhost:4318/v1/traces ./gradlew :app:server:run
   UKPT_TRACE_EXPORT=http://localhost:4318/v1/traces ./gradlew :app:client:desktop:hotRunAsync
   ```
   Gradle passes the variable to the app processes. A process that already runs without the
   variable exports nothing; restart the process.
3. Perform the action, by hand or with `ukpt-drive-app`. The exporter posts spans every second.
4. Stop Jaeger with `./gradlew traceViewerStop`. Stopping Jaeger discards the traces.

## Read

```
python3 -I .agents/skills/ukpt-traces/scripts/traces.py recent --service ukpt-desktop --minutes 15
python3 -I .agents/skills/ukpt-traces/scripts/traces.py show <trace id>
```

`recent` prints one line per trace: start time, trace ID, root service, root span and span count.
`show` prints the trace as a tree with each span's service, name and duration, and notes:

- `links <trace>/<span>`: the span starts a trace and links to another trace's span. A ViewModel
  intent links to the visit of its screen; `show` that trace ID to read the visit.
- `parent … not stored`: Jaeger stores no span with that ID. The parent is still running in a
  process that has not exported it, or the parent's process does not export.
- `Cancelled`, `Threw(…)`: the call's outcome when the call did not return normally.

## Span shapes

- `Class.function`: one call. A server handler's parent is the client call that sent the request.
- `(collect)` and `(first value)`: one collection of a returned Flow, and the time from the start
  of the collection to the first value.
- `(visit)`: one visit of a screen, from the destination's first open to its close. A visit is the
  root of its own trace.
- A `(collect)` or `(visit)` span is exported when it starts, with a duration of 0 ms and the
  outcome `Running`. Its `(closed)` child is exported when the span ends and holds the span's
  duration and outcome. A screen that is still open has no `(closed)` child.

The Jaeger query API (`/api/v3/services`, `/api/v3/traces`, `/api/v3/traces/{id}`) returns OTLP
JSON; `traces.py` reads the same endpoints. Jaeger v2 does not serve the v1 `/api/services` path.
