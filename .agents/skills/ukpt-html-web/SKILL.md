---
name: ukpt-html-web
description: >-
  Build the Compose HTML (DOM) web client, :app:client:web-html — Compose HTML
  screens in a feature's jsMain as PlatformOverride twins of the Compose UI
  screens, the platform.design.html primitives and ukpt-design.css tokens, the
  jsTest coverage, the bundle gate, the Enro and webpack failure messages, and
  removing the client. Use when adding or changing an HTML screen, when a
  Compose HTML build fails, or when removing the HTML client.
---

# ukpt-html-web

Identifiers here use the template's UKPT identity (`feature.ukpt`, `Ukpt`); projects rename these — the map is in `.ukpt/template.json`.

`:app:client:web-html` renders the feature ViewModels and State with Compose HTML: DOM elements,
no canvas, no skiko. The template builds it beside the canvas client `:app:client:web`; a project
keeps either or both (`ukpt-new-project`, "Choose the web clients").

Every module that applies `id("ukpt.html-web")` has a Kotlin/JS browser target, and its wasmJs
symbol processors also run for JS (`build-logic/src/main/kotlin/ukpt.html-web.gradle.kts`). Every
`:feature:<name>:api` and `:client`, and every platform module a `:client` `commonMain` depends on,
applies it.

## Layout

| What | Where |
|---|---|
| HTML screens | `feature/<name>/client/src/jsMain/kotlin/feature/<name>/client/ui/` |
| HTML screen tests | `feature/<name>/client/src/jsTest/kotlin/feature/<name>/client/ui/` |
| Primitives and class names | `platform/client/design-html/src/jsMain/kotlin/platform/design/html/` |
| Tokens and component CSS | `platform/client/design-html/src/css/ukpt-design.css` |
| App shell | `app/client/web-html` (`Main.kt`, `UkptHtmlNavigation.kt`, `ClientDependencies.kt`) |

The worked example is `:feature:core`: `UkptHtmlScreen.kt` and `ConfirmResetDialogHtmlScreen.kt`,
tested by `UkptHtmlScreenContentTest.kt`.

## Add an HTML screen

For each destination of a feature the HTML client includes:

1. In `src/jsMain/`, same package as the Compose UI screen, declare the twin with an `Html` infix:
   `@Composable @NavigationDestination.PlatformOverride(<Name>Destination::class) fun <Name>HtmlScreen(viewModel: <Name>ViewModel = viewModel())`.
   A dialog is a property:
   `@NavigationDestination.PlatformOverride(<Name>Destination::class) val <name>HtmlDestination = navigationDestinationContent(metadata = { htmlDialog() }) { … }`.
   The infix is required: both declarations compile into the same JS package.
2. Pair it with `internal fun <Name>HtmlScreenContent(state, callbacks…)` in the same file. Render
   the same State branches as the Compose UI screen.
3. Build from `UkptHtmlButton`, `UkptHtmlTypography`, `UkptHtmlColors` and `UkptHtmlLayout`, never
   from literal colours or sizes.
4. In `src/jsTest/`, compose the ScreenContent with `runTest { composition { … } }` from
   `html-test-utils`, then read `root` and click its buttons.

The rules, all run by `verifyArchitecture`:

- `ClientUi.Screen.htmlScreenIsPlatformOverride` — the annotation in step 1.
- `ClientUi.htmlSourceUsesNoComposeUi` — no Compose UI, Compose resources, udytils `:ui` or
  `platform.design` imports in `jsMain` (`platform.design.html` is allowed).
- `ClientUi.htmlSourceDeclaresOnlyComposables` — State, ViewModels and value types stay in
  `commonMain`.
- `ClientUi.Composable.htmlScreenContentTested` — step 4.
- `FeatureRules.DependencyModule.registeredInEveryClientShell` — every `<name>ClientDependencies`
  is in both `ClientDependencies.kt` files.

## Design tokens

`ukpt-design.css` restates `UkptColors` (Light, and Dark under `prefers-color-scheme: dark`),
`UkptSpacing`, `UkptShapes` and the `UkptTypography` sizes, line heights and weights as
`--ukpt-*` custom properties. `UkptDesignCssTest` (`:platform:client:design-html:jvmTest`) fails
and lists each property that differs from the Compose value. After a
token change in `:platform:client:design`, change the CSS to match.

Fonts are not checked: `UkptFonts.System` is the platform default, and the CSS names a system font
stack. A project that bundles a typeface also adds an `@font-face` rule to the CSS.

Kotlin/JS does not copy a library's resources into the app, so `:app:client:web-html` resolves the
stylesheet through the `htmlStylesheetElements` configuration in
`platform/client/design-html/build.gradle.kts` and copies it into its own resources.

## Run and verify

```
./gradlew :app:client:web-html:jsBrowserDevelopmentRun
./gradlew :feature:core:client:jsTest
./gradlew :app:client:web-html:check
```

`check` builds the production bundle and runs `verifyHtmlBundle`. In the served app, open the
console and navigate to every changed screen: a missing destination fails only when it is opened.
Deploying: `ukpt-web-deploy`, "Compose HTML client".

## Failure messages

| Message | Cause | Fix |
|---|---|---|
| KSP: `… renders with Compose HTML, but these destinations are Compose UI functions` | A `@Composable` screen the HTML client compiles has no `PlatformOverride` twin. | Add the HTML screen for each listed key. |
| KSP warning: `… Enro can't check whether these destinations use Compose UI` | A property destination (`navigationDestination {}`) has no override, and Enro can't see its builder. | Add an HTML twin for each listed key that renders Compose UI. |
| webpack: `Can't resolve './skiko.mjs'` | Compose UI reached the bundle. | As the next row. |
| `Compose UI is in the Compose HTML bundle: …` (`verifyHtmlBundle`) | A Compose UI screen or dependency is reachable from `Main.kt`. | Add the missing HTML screens; remove Compose UI dependencies from `:app:client:web-html`. |
| Browser console: `No binding found for <Key>` | The key's destination isn't on the HTML client's classpath. | Add the feature client to `web-html`'s `jsMain`, or add the HTML screen. |
| Browser console: `Found multiple bindings for <Key>` | An HTML destination uses `@NavigationDestination` instead of `PlatformOverride`. | Change the annotation. |

## Not supported yet

udytils' `ConfirmationDestination` and the `ErrorDialogDestination` that `ErrorHandler` opens have
no Compose HTML destinations, and their keys hold Compose resources text. A screen the HTML client
reaches uses a feature dialog destination instead (`ConfirmResetDestination` is the example), and
renders errors from its State.

## Removing the HTML client

A project that won't ship it deletes:

- `app/client/web-html` and `platform/client/design-html`, and their `settings.gradle.kts` lines;
- each feature's `src/jsMain/` and `src/jsTest/`;
- `kotlin-js-store/yarn.lock`, the Kotlin/JS lock file (`kotlin-js-store/wasm/` is the canvas
  client's);
- every `id("ukpt.html-web")` line, and the `jsMain`/`jsTest` dependency blocks in the `:client`
  builds.

`build-logic` and the version catalog are template-owned: leave the `ukpt.html-web` plugin and the
`compose-html*` entries in place. `registeredInEveryClientShell` then checks only
`:app:client:common`. Then run the `ukpt-verify` sweep without the Compose HTML client section.
