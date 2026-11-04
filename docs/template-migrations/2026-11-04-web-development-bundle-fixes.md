# Web development bundle fixes

Two build fixes for the web clients. The file sync carries them into `app/client/web` and
`app/client/web-html`. Apply them by hand to any other module with a Kotlin/JS or Kotlin/Wasm
browser executable.

- **Compose HTML development bundle.** `jsBrowserDevelopmentWebpack`, and so
  `jsBrowserDevelopmentRun`, failed with `Can't resolve './skiko.mjs'`. A development build skips
  dead-code elimination, so it keeps Compose UI's `skiko-kjs` module, which the feature clients'
  `commonMain` brings onto the classpath. `app/client/web-html/webpack.config.d/skiko-development.js`
  supplies `./skiko.mjs` as an external with an empty `skikoApi` in development mode only. The
  production bundle is unchanged, and `verifyHtmlBundle` still checks it.
- **Development and production bundles in one invocation.** The development and production
  `*ExecutableCompileSync` tasks write the same `build/<js|wasm>/packages/<module>/kotlin`
  directory, so a build that requested both failed Gradle's implicit-dependency validation. The
  production sync now runs after the development webpack task.

## Detection

```bash
grep -rlE 'binaries\.executable\(\)' --include='build.gradle.kts' app feature platform
```

Each listed module with a `js { browser { … } }` or `wasmJs { browser { … } }` target is affected
by the second fix. A Compose HTML module whose classpath includes Compose UI is affected by the
first.

## Migration

1. For a `js` browser executable, add to its `build.gradle.kts`:
   ```kotlin
   tasks.named("jsProductionExecutableCompileSync") {
       mustRunAfter("jsBrowserDevelopmentWebpack")
   }
   ```
   For a `wasmJs` browser executable, use `wasmJsProductionExecutableCompileSync` and
   `wasmJsBrowserDevelopmentWebpack`.
2. For a Compose HTML module whose development bundle fails with `Can't resolve './skiko.mjs'`, copy
   `app/client/web-html/webpack.config.d/skiko-development.js` into the module's
   `webpack.config.d/`.

## Verification

```bash
./gradlew :app:client:web-html:jsBrowserDevelopmentWebpack :app:client:web-html:jsBrowserDistribution
./gradlew :app:client:web:wasmJsBrowserDevelopmentWebpack :app:client:web:wasmJsBrowserDistribution
```

Run the same pair for each other affected module. Then run `:app:client:web-html:jsBrowserDevelopmentRun`,
open the served page, and check that the first screen renders with no console errors.
