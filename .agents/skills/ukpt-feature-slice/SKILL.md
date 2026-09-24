---
name: ukpt-feature-slice
description: >-
  Scaffold a new feature vertical slice :feature:<name>:{api,server} in UKPT —
  the two build files, the server domain and data, the web layer (Routes, Page,
  Components, Form, event stream), the tests and HTML snapshots, the settings
  include, and the Koin DI + app wiring — modeled on :feature:core. Use when
  adding a new feature module to the project.
---

# ukpt-feature-slice

Scaffold `:feature:<name>:{api,server}` modeled on the canonical `:feature:core`.
`templates.md` carries the build files and source skeletons; `:feature:core` is the living
reference to read when in doubt.

## Naming (read first — `:feature:core` has a deliberate mismatch)
`:feature:core` uses Gradle path segment `core` but Kotlin package `feature.ukpt` and type prefix
`Ukpt`. For a NEW feature, be **internally consistent** and use `<name>` everywhere:
- path `:feature:<name>:{api,server}`; Kotlin package `feature.<name>`;
- DI val `<name>ServerDependencies`; static files under `src/main/resources/static/<name>/`;
- types `<Name>Routes`, `<Name>Paths`, `<Name>Ids`, and `<Page>Page`/`<Page>PageState` per page
  (`<Name>` = PascalCase).

Do **not** copy the literal `ukpt`/`Ukpt` from core — substitute `<name>`/`<Name>`.
`ukptLayout` is the project's layout function in `:platform:server:web`; it carries the project's
prefix, not the feature's. A shell that several features share (navigation, the signed-in user) is
a Layout of its own over `ukptDocument` (`ServerWeb.Layout`).

## Domain shape (before writing `server.domain`)
Interfaces come from consumers, not from storage. Before the first `fun interface`:
1. List the consumers (route handlers, event streams, UseCases) and the question each one asks.
2. Write the domain models those answers need; facts one consumer needs together are one model.
3. Derive one interface per question, provided by the Repository that owns the storage. A UseCase
   exists only where a decision, or a composition of independent capabilities, exists, and it is
   declared in its interface's file when both are in the same module and package
   (`ServerDomain.UseCase.declaredInItsInterfaceFile`).
Guidance: `ServerDomain.DomainInterface.namesACapability`, `.readProjections`,
`.collapsedUpdateFamilies`. `ukpt-architecture-review` has the domain contract inventory to check
the result.

## Steps
1. **Module dirs + two `build.gradle.kts`** (templates.md §1–2).
2. **`settings.gradle.kts`** — add two `include(...)` after the `:feature:core` block (templates.md §3).
3. **Domain and data** (`feature.<name>.server.domain`, `feature.<name>.server.data`) — templates.md §4.
4. **Web layer** (`feature.<name>.server.web`) — templates.md §5: `<Name>Paths` and `<Name>Ids`, a
   `<Page>PageState`, the Page and its Components, a `<Thing>Form` for each form, an event stream
   for each live list, and the `<Name>Routes` class.
5. **DI** — `<name>ServerDependencies` at the feature root `feature.<name>`, binding the Routes class
   with `bind WebRoutes::class` (templates.md §6).
6. **Tests** — a route test, an HTML snapshot test, and a test for each UseCase and event stream
   (templates.md §7).
7. **Wire it up** (templates.md §8 checklist):
   - `app/server/build.gradle.kts` → `implementation(projects.feature.<name>.server)`.
   - `app/server/.../ServerDependencies.kt` → add `<name>ServerDependencies` to the list + its import.
     The server installs every `WebRoutes` binding, so nothing else registers the routes.
8. **Verify** — record the HTML goldens (`./gradlew :feature:<name>:server:test -PrecordHtmlSnapshots`),
   review them, then run the `ukpt-verify` sweep. Open the page in a browser once (`ukpt-run`) and
   check the console for content security policy violations.

## Web-layer conventions
- **One handler, two answers.** A handler answers an htmx request (`call.isHtmx`) with a fragment
  rendered by the Component it replaces, and any other request with a full Page or a `303`
  (`call.respondSeeOther(...)`). Call `call.varyOnHtmx()` in a handler whose body depends on it.
  Every form therefore has `action` and `method` as well as `hx { post(...) }`.
- **Invalid input is `422`.** Re-render the form from the submitted `<Thing>Form` and its `Errors`
  with `HttpStatusCode.UnprocessableEntity`; the htmx configuration in the layout swaps `422`.
- **Live lists use server-sent events.** An `<thing>Events(...)` function maps a domain `Flow` of the
  list through `OobList.<parent>(ids.list, key = { it.id }) { item(it) }.let { list -> flow.oobUpdates(list) }`
  into `ServerSentEvent`s; the Routes class sends it from `sse(<Name>Paths.EVENTS)`. The page renders
  the list with the same item Component (`UL.<thing>Item`), and connects with a sink element:
  `div { sse { connect(...); swap(<Name>Ids.EVENT) }; hx { swap(SwapStyle.None) } }`. A mutation
  handler then answers htmx with `204 No Content` or a reset form; the stream updates every tab.
- **Local UI state is Alpine, in a file.** Register components with `Alpine.data("<component>", ...)`
  inside `document.addEventListener("alpine:init", ...)` in `static/<name>/<file>.js`; pass its path
  to `ukptLayout(LayoutState(title, scripts = ...))`. Markup names members only: `alpine { data("..."); on("input", "update"); text("remaining") }`.
  Keep an htmx swap target outside an Alpine root unless resetting its state is intended.
- **Typed markup.** `hx { }`, `sse { }`, `alpine { }` and `elementId =` — never `attributes["hx-…"]`,
  inline `<script>`, `onClick =`, `unsafe { }`, or a CDN URL (`ServerWeb.typedHypermediaAttributes`,
  `ServerWeb.noInlineScript`, `ServerWeb.selfHostedAssets`).
- **Handlers read, Components render.** A Component takes values (`ServerWeb.Component.rendersValuesOnly`);
  the handler reads from the domain and builds the View State.

## Rule cheat-sheet (canonical text in `platform/common/architecture/docs/` — search the ID)
- **`ServerWeb.Routes`** (construct) — `internal class <Name>Routes(...) : WebRoutes`;
  **`ServerWeb.Routes.noPersistenceInjection`** — inject domain interfaces, never a Repository.
- **`ServerWeb.Page.takesItsViewState`** — a Page takes one `<Page>PageState`;
  **`ServerWeb.Page.rendersThroughLayout`** — and renders through `ukptLayout` or a shared Layout.
- **`ServerWeb.Form.rawInputProperties`** — a Form holds strings as submitted.
- **`ServerWeb.noDataImports`**, **`ServerWeb.noKoinImports`** — the web layer imports neither.
- **`FeatureRules.DependencyModule`** (construct) — DI is a `val <name>ServerDependencies` module in `feature.<name>`;
  **`ProjectRules.constructorReferenceBindings`** — constructor-reference bindings.

## Reference
- The living template: `feature/core/{api,server}` (build files + `src/.../feature/ukpt/...`), and
  `feature/core/server/src/main/resources/static/ukpt/`.
- The web platform: `platform/server/web` (`WebRoutes`, `ukptLayout`, `ukptDocument`, `textField`, `FormResult`,
  `respondSeeOther`, `installWebPlatform`).
- Skeletons + the wiring checklist: `templates.md` (this skill).
