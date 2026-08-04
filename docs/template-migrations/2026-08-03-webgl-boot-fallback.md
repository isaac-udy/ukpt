# Web boot shows a WebGL message instead of a blank page

The web target renders with skiko, which acquires a **WebGL** context at startup. When the browser
cannot provide one — hardware acceleration disabled, GPU blocklisted, or WebGL contexts exhausted —
skiko dies with an uncaught `getParameter of undefined` during Compose's first frame, and the user
sees a **blank page** with no explanation.

The scaffold `index.html` now gates the app bundle behind a plain-DOM WebGL probe: it loads the
bundle only when a context is available, and otherwise replaces the boot screen with an actionable
message. The fallback is deliberately not Compose — the renderer is the thing that failed, so a
Compose error screen would fail the same way.

## Detection

A project is affected if its web `index.html` loads the app bundle from static markup with no WebGL
check:

```bash
grep -L "webGlAvailable" app/client/web/src/wasmJsMain/resources/index.html
```

The file being listed (the guard is absent) means the project still shows a blank page when WebGL is
unavailable.

## Migration

`index.html` is downstream-customized (boot screen, branding), so this is not a file sync — apply
the guard by hand:

1. Stop loading the app bundle from a static `<script src="…App.js">` tag.
2. Before load, probe for a context and inject the bundle only when one exists:

   ```js
   function webGlAvailable() {
     try {
       var c = document.createElement("canvas");
       var gl = c.getContext("webgl2") || c.getContext("webgl") || c.getContext("experimental-webgl");
       if (!gl) return false;
       var lose = gl.getExtension("WEBGL_lose_context");   // free the probe context
       if (lose) lose.loseContext();
       return true;
     } catch (e) { return false; }
   }
   ```

3. When the probe fails, replace the boot screen with a message that names the cause (hardware
   acceleration / WebGL) and a next step (turn it on, or try another browser). Reuse your boot
   screen's tokens so it stays on-brand.

The scaffold `app/client/web/src/wasmJsMain/resources/index.html` is the reference shape.

## Verification

Serve the web app, turn hardware acceleration off in the browser (Chrome or Edge: Settings →
System), and reload: the message appears in place of a blank page and no uncaught exception is
logged. Turn it back on and confirm the app boots normally.
