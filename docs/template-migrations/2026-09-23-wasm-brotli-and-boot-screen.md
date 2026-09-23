# The web build writes brotli-compressed wasm and the page shows a boot screen

`app/client/web/webpack.config.d/` holds two webpack plugins. `wasm-brotli.js` writes a
`<hash>.wasm.br` beside each `.wasm` in production builds. `wasm-manifest.js` writes
`wasm-manifest.js`, which records the uncompressed size of each `.wasm`, in every build.
`index.html` sets `<base href="/">` and shows a boot screen with a download progress bar until
`Main.kt` reports the first drawn frame. The new `ukpt-web-deploy` skill describes the headers and
cache policy a host needs.

`UkptTheme` now maps material's surface-container roles (`surfaceContainer`,
`surfaceContainerHigh` and the rest of that family) to the palette's `surface`. Material components
that paint their container with those roles — `AlertDialog`, menus, bottom sheets, cards — used
material's light defaults under both palettes, which put the dark palette's light ink on a light
dialog.

The file sync carries the webpack plugins, the `UkptTheme` change and the skill. Projects apply the
rest by hand: an `index.html` or `Main.kt` the project changed, a `UkptTheme` the project changed,
and the project's deploy pipeline, which the template does not contain.

## Detection

```bash
ls app/client/web/webpack.config.d/wasm-brotli.js app/client/web/webpack.config.d/wasm-manifest.js
grep -n "wasm-manifest.js\|base href" app/client/web/src/wasmJsMain/resources/index.html
grep -n "__appFirstFrame" app/client/web/src/wasmJsMain/kotlin/**/Main.kt
grep -n "surfaceContainerHigh" platform/client/design/src/commonMain/kotlin/**/*Theme.kt
```

A project with a deploy pipeline for the web client is affected by step 5 whether or not the file
sync applied cleanly. A project that already compresses the wasm in its pipeline, or has its own
wasm manifest plugin or boot screen, is affected by step 6.

## Migration

1. Take `wasm-brotli.js` and `wasm-manifest.js` into `app/client/web/webpack.config.d/`.
2. Merge the template's `index.html` into the project's. A project that restyled the page keeps its
   styling and takes the `<base href="/">`, the `wasm-manifest.js` script tag and the boot script.
   The boot screen's colours restate the design system's Light and Dark palettes as CSS custom
   properties on `:root`; a project with its own palette restates its own values there. A project
   served from a sub-path sets the base to that path.
3. Add `DismissBootScreenOnFirstFrame()` inside `ComposeViewport` in the web `Main.kt`, with its
   `notifyFirstFrameDrawn()` helper, as in the template. Without it the boot screen stays up for its
   8-second safety-net timeout after Compose attaches.
4. A project that changed its `<Prefix>Theme` wrapper adds the seven `surfaceContainer*`,
   `surfaceBright` and `surfaceDim` roles to its material colour scheme, mapped to its surface token.
   Screens that set a dialog's or card's `containerColor` explicitly to work around the defaults can
   drop the override.
5. Update the deploy pipeline as `ukpt-web-deploy` describes. On an object store or a server
   without automatic compression, upload each `.wasm.br` under its `.wasm` name with
   `Content-Encoding: br` and `Content-Type: application/wasm`, exclude the wasm from the bulk sync,
   remove binaries from earlier deploys, and upload `wasm-manifest.js` with `no-cache` alongside
   `index.html` and `App.js`. On a host that compresses at its edge, exclude `*.br` from the upload.
   Unknown paths must be answered with `index.html`.
6. A pipeline step that already brotli-compresses the wasm is removed; the build now writes the
   `.br` files. A project's own wasm manifest plugin or boot screen is replaced by the template's,
   or kept in place of it — the two manifest plugins emit the same asset name and must not both run.

## Verification

```bash
./gradlew :app:client:web:wasmJsBrowserDistribution --no-configuration-cache
ls app/client/web/build/dist/wasmJs/productionExecutable/*.wasm.br \
   app/client/web/build/dist/wasmJs/productionExecutable/wasm-manifest.js
./gradlew :feature:core:client:verifyPaparazzi --no-configuration-cache
```

The build log lists each binary's compression ratio under `LOG from WasmBrotli`. Goldens of
dialogs, menus, sheets and cards change with the theme fix and are re-recorded after review. After
the next deploy, run the curl check in `ukpt-web-deploy` against the host and reload a deep link.
