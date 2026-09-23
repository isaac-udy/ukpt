---
name: ukpt-web-deploy
description: >-
  Serve the web (wasmJs) client from a static host — the brotli-compressed
  `.wasm.br` files the production build writes, the Content-Encoding and
  Content-Type headers they need, cache headers by file-name stability, recipes
  for object stores, nginx, Caddy and edge-compressing hosts, the boot progress
  bar's manifest, deep-link fallback, and the curl check for a deployed host.
  Use when deploying the web client or writing a web deploy pipeline.
---

# ukpt-web-deploy

The template ships no deploy pipeline. This skill describes what the production build produces and
what a host must do with it; the pipeline itself belongs to the project.

## What the build produces

```
./gradlew :app:client:web:wasmJsBrowserDistribution --no-configuration-cache
```

writes `app/client/web/build/dist/wasmJs/productionExecutable/`:

| File | Name | Written by |
|---|---|---|
| `<hash>.wasm` (the app module and skiko) | content-hashed | Kotlin/Wasm through webpack |
| `<hash>.wasm.br` | content-hashed | `webpack.config.d/wasm-brotli.js`, production builds only |
| `index.html`, `App.js`, `wasm-manifest.js` | stable | webpack; `wasm-manifest.js` by `webpack.config.d/wasm-manifest.js` |
| `composeResources/**` | stable | Compose resources |

The build log prints the compression ratio of each binary under `LOG from WasmBrotli`.

## The serving contract

A `.wasm` request is answered with the bytes of its `.br` sibling and these headers:

```
Content-Encoding: br
Content-Type: application/wasm
Cache-Control: public, max-age=31536000, immutable
```

- The page requests `<hash>.wasm`, and the browser removes the brotli encoding before
  `WebAssembly.instantiateStreaming` reads the body. The `.br` file is a build output only. A host
  must not serve it under its own name.
- `WebAssembly.instantiateStreaming` rejects any `Content-Type` other than `application/wasm`.
  Upload tools infer the type from the source file's extension, so an upload from `<hash>.wasm.br`
  must set the type explicitly. A wrong type shows as a console `TypeError` about the MIME type.
- The host may send the brotli bytes regardless of the request's `Accept-Encoding`: Compose for web
  requires WebAssembly GC, and every browser that implements it accepts brotli over HTTPS.
- Object stores serve the bytes and headers they hold, and CDN dynamic compression leaves
  `application/wasm` uncompressed. On those hosts nothing compresses the wasm unless the deploy
  uploads the `.br` bytes.

## Hosts

| Host | Configuration |
|---|---|
| Edge-compressing hosts (Firebase Hosting, Cloudflare, Netlify, Vercel) | Deploy the directory without the `.br` files. Confirm with the curl check that the edge compresses `application/wasm`; if it does not, the host needs one of the recipes below. |
| nginx | `brotli_static on;` (ngx_brotli) answers a request for `<file>` with `<file>.br` when the client accepts br. `types` must map `wasm` to `application/wasm`. |
| Caddy | `file_server { precompressed br }`. |
| Object store (GCS, S3) behind a CDN | Upload each `.br` file under its `.wasm` key with explicit headers, as below. Leave the CDN's dynamic compression on for the text assets; it skips responses that already carry a `Content-Encoding`. |

### Object-store upload

The rest of the directory goes up with a sync command, which must exclude the wasm, the `.br`
files, and the stable-named files. A sync compares object size with the local file; the stored
object holds the compressed bytes, so a sync that includes the wasm re-uploads the uncompressed file
on every deploy. Stable-named files go up in their own pass with their own `Cache-Control`.

With `gcloud storage` (`aws s3 sync` / `aws s3 cp` take the equivalent `--exclude`,
`--content-encoding`, `--content-type` and `--cache-control` flags):

```bash
SITE=app/client/web/build/dist/wasmJs/productionExecutable
IMMUTABLE="public, max-age=31536000, immutable"

gcloud storage rsync --recursive --delete-unmatched-destination-objects \
  --exclude='^composeResources/.*$|^[^/]*\.wasm(\.br)?$|^index\.html$|^App\.js$|^wasm-manifest\.js$' \
  --cache-control="$IMMUTABLE" "$SITE" "gs://$BUCKET"

for name in index.html App.js wasm-manifest.js; do
  gcloud storage cp --cache-control="no-cache, max-age=0" "$SITE/$name" "gs://$BUCKET/$name"
done
gcloud storage rsync --recursive --cache-control="public, max-age=3600" \
  "$SITE/composeResources" "gs://$BUCKET/composeResources"

for file in "$SITE"/*.wasm; do
  gcloud storage cp --content-encoding=br --content-type=application/wasm \
    --cache-control="$IMMUTABLE" "$file.br" "gs://$BUCKET/$(basename "$file")"
done

# The exclusion also keeps the wasm out of --delete-unmatched-destination-objects, so binaries
# from earlier deploys are removed here.
{ gcloud storage ls "gs://$BUCKET/*.wasm" 2>/dev/null || true; } | while read -r object; do
  [ -f "$SITE/$(basename "$object")" ] || gcloud storage rm "$object"
done
```

Invalidate the CDN cache for the site after the upload. The bucket needs `index.html` as its
not-found page for deep links (below).

## Cache headers

| Files | Cache-Control |
|---|---|
| `<hash>.wasm` and other content-hashed files | `public, max-age=31536000, immutable` |
| `index.html`, `App.js`, `wasm-manifest.js` | `no-cache, max-age=0` |
| `composeResources/**` | `public, max-age=3600` |

A stable-named file cached as immutable serves the old bundle until the cache entry expires.
`wasm-manifest.js` has a stable name, and a stale copy records the sizes of binaries the page no
longer loads. Upload stable-named files in a pass of their own rather than correcting their headers
after a bulk upload: a sync compares size and modification time, which differ for files restored
from a CI artifact, so each deploy re-uploads them with the bulk pass's header.

## Boot screen and progress bar

`index.html` shows a boot screen until Compose draws its first frame (`Main.kt` signals it). While
the wasm binaries download, the boot screen shows a progress bar that counts decoded bytes against
the uncompressed total in `wasm-manifest.js`. It does not read `Content-Length`: on a compressed
response that header is the compressed size, or absent, while the stream the page reads delivers
decoded bytes. The bar stays hidden when the download will finish quickly, and the page shows the
spinner when `wasm-manifest.js` is missing.

## Deep links

On web, `rememberRootNavigationContainer` (`app/client/common`) installs Enro's web history plugin
and starts the root container on the destination whose `@NavigationPath` matches the address bar.
The address bar shows the path of the deepest active destination that has one, so a reload of
`/some/screen` requests that path from the host. The host must answer unknown paths with `index.html` (a not-found page on an object
store, `try_files $uri /index.html` on nginx, `try_files {path} /index.html` on Caddy, a rewrite
rule on edge hosts). `index.html` sets `<base href="/">`, so its relative script URLs resolve from
the site root at any path. A site served from a sub-path changes the base to that path.

## Local development

`wasmJsBrowserDevelopmentRun` and the `ukpt-verify-web` bundle gate build in development mode, which
writes no `.br` files and serves uncompressed wasm. The compressed path runs only on a host.

## Verify a deploy

Take a wasm name from the deployed page's network requests, then:

```bash
curl -sI -H 'Accept-Encoding: br, gzip' "https://<host>/<hash>.wasm" \
  | grep -iE '^(HTTP|content-encoding|content-type|content-length|cache-control)'
```

Expected: status 200, `content-type: application/wasm`, `content-encoding: br`,
`cache-control: public, max-age=31536000, immutable`, and a `content-length` equal to the local
`.wasm.br` file's size. In the browser's network panel the wasm rows show a transferred size well
below the resource size, and the app boots. Reload a deep link and confirm the app boots there too.

## Checklist

- [ ] `.br` bytes uploaded under the `.wasm` name, never under `.wasm.br`.
- [ ] `Content-Type: application/wasm` and `Content-Encoding: br` set explicitly on those uploads.
- [ ] Wasm excluded from the bulk sync, with a pass that removes binaries from earlier deploys.
- [ ] `index.html`, `App.js` and `wasm-manifest.js` uploaded with `no-cache` in their own pass.
- [ ] CDN dynamic compression left on for text, and the CDN cache invalidated after the deploy.
- [ ] Unknown paths answered with `index.html`.
- [ ] Deployed host checked with `curl -I` and the network panel.
