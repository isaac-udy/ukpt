#!/usr/bin/env python3
"""Lists and prints the traces that a local Jaeger stores, through Jaeger's v3 query API.

  traces.py recent [--service ukpt-desktop] [--minutes 15]
  traces.py show <trace id>
"""
import argparse
import datetime
import json
import sys
import urllib.parse
import urllib.request

JAEGER = "http://localhost:16686"


def get(path, params=None):
    url = JAEGER + path + ("?" + urllib.parse.urlencode(params) if params else "")
    try:
        with urllib.request.urlopen(url, timeout=10) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return {"result": {"resourceSpans": []}}
        raise
    except urllib.error.URLError:
        sys.exit(f"Jaeger is not running on {JAEGER}. Run ./gradlew traceViewerStart.")


def spans_of(document):
    spans = []
    for resource in document.get("result", {}).get("resourceSpans", []):
        service = next((a["value"].get("stringValue") for a in resource["resource"].get("attributes", []) if a["key"] == "service.name"), "?")
        for scope in resource.get("scopeSpans", []):
            for span in scope.get("spans", []):
                span["service"] = service
                span["attrs"] = {a["key"]: a["value"].get("stringValue") for a in span.get("attributes", [])}
                spans.append(span)
    return spans


def millis(span):
    return (int(span["endTimeUnixNano"]) - int(span["startTimeUnixNano"])) / 1e6


def recent(service, minutes):
    now = datetime.datetime.now(datetime.timezone.utc)
    fmt = "%Y-%m-%dT%H:%M:%S.%fZ"
    spans = spans_of(get("/api/v3/traces", {
        "query.service_name": service,
        "query.start_time_min": (now - datetime.timedelta(minutes=minutes)).strftime(fmt),
        "query.start_time_max": now.strftime(fmt),
    }))
    traces = {}
    for span in spans:
        traces.setdefault(span["traceId"], []).append(span)
    rows = []
    for trace_id, members in traces.items():
        ids = {s["spanId"] for s in members}
        roots = [s for s in members if s.get("parentSpanId") not in ids] or members
        first = min(roots, key=lambda s: int(s["startTimeUnixNano"]))
        rows.append((int(first["startTimeUnixNano"]), trace_id, first["service"], first["name"], len(members)))
    for start, trace_id, root_service, root_name, count in sorted(rows):
        at = datetime.datetime.fromtimestamp(start / 1e9).strftime("%H:%M:%S")
        print(f"{at}  {trace_id}  {root_service}  {root_name}  ({count} spans)")


def show(trace_id):
    spans = spans_of(get(f"/api/v3/traces/{trace_id}"))
    if not spans:
        sys.exit(f"Jaeger stores no trace {trace_id}.")
    ids = {s["spanId"] for s in spans}
    children = {}
    for span in spans:
        children.setdefault(span.get("parentSpanId"), []).append(span)
    roots = [s for s in spans if s.get("parentSpanId") not in ids]

    def line(span, depth):
        notes = []
        if span.get("parentSpanId") and span["parentSpanId"] not in ids:
            notes.append(f"parent {span['parentSpanId']} not stored")
        outcome = span["attrs"].get("trace.outcome", "")
        if outcome and outcome != "Returned":
            notes.append(outcome)
        for link in span.get("links", []):
            notes.append(f"links {link['traceId']}/{link['spanId']}")
        suffix = f"  [{'; '.join(notes)}]" if notes else ""
        print(f"{'  ' * depth}{span['service']}  {span['name']}  {millis(span):.1f} ms{suffix}")
        for child in sorted(children.get(span["spanId"], []), key=lambda s: int(s["startTimeUnixNano"])):
            line(child, depth + 1)

    for root in sorted(roots, key=lambda s: int(s["startTimeUnixNano"])):
        line(root, 0)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    recent_parser = commands.add_parser("recent")
    recent_parser.add_argument("--service", default="ukpt-desktop")
    recent_parser.add_argument("--minutes", type=int, default=15)
    show_parser = commands.add_parser("show")
    show_parser.add_argument("trace_id")
    args = parser.parse_args()
    if args.command == "recent":
        recent(args.service, args.minutes)
    else:
        show(args.trace_id)


main()
