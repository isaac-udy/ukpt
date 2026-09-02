---
name: ukpt-drive-app
description: >-
  Drive the running desktop app from an agent through the Compose Hot Reload MCP
  server — launch it, read the semantic tree, click and type, take screenshots,
  hot-reload after a code change, and read runtime errors and logs. Use when a
  change needs confirming in the real app, when reproducing a UI bug, or when
  asked to exercise a screen or flow end to end. Desktop only.
---

# ukpt-drive-app

The repo-root `.mcp.json` registers an MCP server named `compose-hot-reload`. It is the
`hotMcpServer` task of `:app:client:desktop`, bundled with the Compose Multiplatform Gradle plugin
(Compose Hot Reload 1.2.0+). The server does not launch the app: it attaches to a desktop app that
was started with `hotRun`, then proxies tool calls into that process.

Desktop only. There is no equivalent for the web, Android, or iOS targets. Because the UI is in
common code, the desktop app is the surface for checking shared screens.

## Tools

| Tool | What it does |
|---|---|
| `status` | Reports `connected`, `reloadState` (`ok` / `reloading` / `failed`), the last reload error, and windows currently failing to render. Call it first. |
| `list_windows` | Window ids, titles and bounds. Every window-targeting tool defaults to the first window; pass `window_id` to pick another. |
| `get_semantic_tree` | JSON tree of nodes with `id`, `role`, `text`, `bounds`, state flags and `actions`. Dialogs and popups appear as separate roots. |
| `take_screenshot` | PNG of the Compose content. Pass `save_to` with an absolute path, then open the file to look at it. |
| `click`, `long_click` | Act on a node by `nodeId`. The node must list the action in `actions`. |
| `type_text`, `scroll`, `scroll_to_index` | Text entry and scrolling on a node. |
| `reload` | Recompiles and hot-reloads changed classes into the running app. Returns `reloaded: false` when nothing changed. |
| `await_reload` | Only when the app was started with continuous build (`--auto`). |
| `get_ui_error` | Message and stacktrace for a window whose composition threw. |
| `get_logs` | Recent stdout/stderr lines from the app process. |
| `restart` | Restarts the app process with the same arguments. |
| `reset_ui` | Drops the composition so all `remember`-ed state is discarded. |
| `resize_window` | Sets the window size in pixels. |

Bounds in the semantic tree are physical pixels, so on a 2x display an 800x600 window reports
1600x1200.

## Procedure

1. **Check the tools are loaded.** Claude Code discovers MCP servers at session start from the
   `.mcp.json` in the directory it was launched from. If no `compose-hot-reload` tools are listed,
   the session must be restarted from the repository root; there is no way to load them mid-session.

2. **Start the server when the flow needs it.** Screens whose ViewModel calls a `@Urpc` service
   show their loading or error state until `./gradlew :app:server:run` is up (see `ukpt-run` for
   the dev database it starts). The template's `:feature:core` Greet flow is one such screen.
   Start the server in the background and wait for its banner before launching the client.

3. **Launch the app under hot reload.**
   ```
   ./gradlew :app:client:desktop:hotRunAsync
   ```
   `hotRunAsync` returns as soon as the app process has been forked; `hotRun` blocks for the app's
   lifetime and must be backgrounded. Either task provisions the JetBrains Runtime through the foojay toolchain
   resolver on first use. The app writes a pid file under the desktop module's build directory
   (build/run/main/main.pid); the MCP server watches that file to find the process.

4. **Poll `status` until `connected` is true.** The server needs a few seconds after the app
   starts to attach. Every other tool returns "No application is currently connected" before then.

5. **Read, then act.** Call `get_semantic_tree`, pick the `nodeId` of the target, then `click` or
   `type_text`. State changes driven by a ViewModel are asynchronous: read the tree again after
   acting, and take a screenshot when layout or styling is what is being checked.

6. **After editing source, `reload`.** A successful reload re-runs the dirty composables in place
   and keeps `remember`-ed and ViewModel state. When the response is `success: false`, the compiler
   output is in `status` under `lastErrorDetails`. When a window renders blank or `status` lists it
   in `uiErrorWindows`, call `get_ui_error`.

7. **`restart` when hot reload cannot apply the change.** Changes to Koin module wiring,
   constructor signatures, top-level `val` initialisers, and anything read once at startup need a
   process restart. `reset_ui` is enough for changes whose only stale state is in `remember`.

8. **Stop the app when done.** Kill the process in the pid file, or close the window. Only one
   hot-reload app can be attached at a time, and a second launch shuts the first down.

## Constraints

- The MCP server is `./gradlew --no-daemon`, so it is a second Gradle JVM beside the build daemon
  and the app JVM. Treat a session with the server attached as one of the machine's two build
  slots (see "Building as an agent" in `UKPT.md`), and do not run the full compile sweep while
  the app is attached.
- `reload` compiles through the same Gradle build as everything else. A compile failure elsewhere
  in the desktop classpath blocks the reload even if the edited file is fine.
- Screenshots and the semantic tree cover Compose content only. Native window chrome, file
  dialogs, and system menus are not visible to the tools.
- The dev-tools overlay that Compose Hot Reload opens beside the app is a separate window and is
  not listed by `list_windows`.
