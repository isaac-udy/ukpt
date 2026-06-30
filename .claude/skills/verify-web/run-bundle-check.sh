#!/usr/bin/env bash
# Bundle-gate for the web/wasm client: runs the webpack bundle and flags the two
# build-time failure signatures. Modes 2 (EnroBrowserContent) and 3 (Koin VM
# factory) are RUNTIME-only — this script cannot see them. For those, serve and
# eyeball the browser:  ./gradlew :app:client:web:wasmJsBrowserDevelopmentRun
set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 1

# Pre-empt the macOS .DS_Store IC crash (the build hook does this too; belt + braces).
find app/client/web/build -name .DS_Store -delete 2>/dev/null

LOG="$(mktemp)"
./gradlew :app:client:web:wasmJsBrowserDevelopmentWebpack 2>&1 | tee "$LOG"
STATUS=${PIPESTATUS[0]}

if grep -qE "UnhandledSchemeError|node:" "$LOG"; then
  echo ""
  echo ">> mode 1 — 'node:' import: a JVM/native dep (e.g. ktor-client-cio) leaked into"
  echo "   common/wasm. Web must use ktor-client-js only (see app/client/web/build.gradle.kts)."
fi
if grep -q "can not find removed library name" "$LOG"; then
  echo ""
  echo ">> mode 4 — .DS_Store poisoned the wasm IC cache. Re-run after the find -delete"
  echo "   above; confirm the purge hook in app/client/web/build.gradle.kts."
fi

if [ "$STATUS" -eq 0 ]; then
  echo ""
  echo "BUNDLE OK. Now run the RUNTIME gate (this script can't check it):"
  echo "  ./gradlew :app:client:web:wasmJsBrowserDevelopmentRun"
  echo "Open the URL, confirm it renders, open the console, navigate to the changed"
  echo "screen(s). Watch for: blank page (Main.kt / EnroBrowserContent) and"
  echo "'Factory.create(...) is not implemented' (missing viewModelOf registration)."
fi
rm -f "$LOG"
exit "$STATUS"
