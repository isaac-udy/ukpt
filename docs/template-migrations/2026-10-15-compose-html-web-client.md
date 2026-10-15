# Compose HTML web client

The template has a second, opt-in web client, `:app:client:web-html`, that renders the feature
ViewModels and State with Compose HTML. It is built only when `ukpt.htmlWebClient` is true in
`gradle.properties`, which is false by default. The `ukpt-html-web` skill describes it.

These arrive with the change:

- `ukpt.htmlWebClient=false` in `gradle.properties`, and the conditional
  `include(":app:client:web-html")` in `settings.gradle.kts`.
- The `ukpt.html-web` convention plugin and `ukpt.isHtmlWebClientEnabled` in `build-logic`.
- `app/client/web-html` and `platform/client/design-html` (always included; its `jvmTest` checks
  `ukpt-design.css` against the Compose design tokens).
- Version catalog entries `enro-html`, `compose-runtime`, `compose-htmlCore` and
  `compose-htmlTestUtils`.
- `id("ukpt.html-web")` and `if (isHtmlWebClientEnabled)` source-set blocks in the `:feature:core`
  builds and `:platform:client:design`.

Five architecture rules are added. Four check only `src/jsMain/` code in `client.ui`:
`ClientUi.htmlSourceUsesNoComposeUi`, `ClientUi.htmlSourceDeclaresOnlyComposables`,
`ClientUi.Screen.htmlScreenIsPlatformOverride` and `ClientUi.Composable.htmlScreenContentTested`.
The fifth, `FeatureRules.DependencyModule.registeredInEveryClientShell`, requires every `:client`
dependency module to be listed in each `app/client/*/…/ClientDependencies.kt`, which now includes
the HTML client's. No rule ID was renamed or removed.

Separately, `app/client/web/build.gradle.kts` no longer reads `project.projectDir` inside
`commonWebpackConfig`. That read was what stopped `wasmJsBrowser*` tasks storing the configuration
cache; they no longer need `--no-configuration-cache`.

## Detection

```bash
test -d app/client/web-html || echo "no Compose HTML client"
grep -n 'static(project.projectDir.path)' app/client/web/build.gradle.kts
grep -L 'ukpt.html-web' feature/*/api/build.gradle.kts feature/*/client/build.gradle.kts
```

## Migration

1. In `app/client/web/build.gradle.kts`, bind `val devServerRoot = layout.projectDirectory.asFile.path`
   inside `wasmJs { }`, before `browser { }`, and pass `static(devServerRoot)`. Remove
   `--no-configuration-cache` from `wasmJsBrowser*` invocations in scripts and run configurations.
2. Decide whether the project keeps the Compose HTML client.
   - **Remove it:** follow "Removing the HTML client" in the `ukpt-html-web` skill.
   - **Keep it:** apply the project's identity to the new files (package, `Ukpt` prefix,
     `--ukpt-` CSS properties). Then, for each feature:
     1. Apply `id("ukpt.html-web")` in its `:api` and `:client` builds, and add the `jsMain` and
        `jsTest` blocks from `ukpt-feature-slice` `templates.md` §2 to its `:client` build.
     2. Add its `<name>ClientDependencies` to
        `app/client/web-html/src/jsMain/kotlin/com/isaacudy/ukpt/ClientDependencies.kt`, and its
        `:client` to that module's `jsMain` dependencies.
     3. Apply `id("ukpt.html-web")` to every platform module its `:client` `commonMain` depends on.
3. If the project kept the client, restate its design tokens in
   `platform/client/design-html/src/css/ukpt-design.css`.
4. HTML screens are needed only before setting `ukpt.htmlWebClient=true`. With the switch on, Enro's
   code generator lists each Compose UI screen that has no HTML twin.

## Verification

```bash
./gradlew verifyArchitecture
./gradlew :platform:client:design-html:jvmTest
./gradlew :app:client:web:wasmJsBrowserDevelopmentWebpack
```

If the project kept the client, also run the Compose HTML gates in `ukpt-verify` with
`-Pukpt.htmlWebClient=true`.
