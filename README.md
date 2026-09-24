# UKPT — htmx

**Udy Kotlin Project Template, `htmx` branch.** A Kotlin server that renders HTML and updates it with
htmx: the server is the application, and there is no client app to build. The architecture is
enforced by tests, not by convention.

This branch is a flavour of [UKPT](https://github.com/isaac-udy/ukpt). It shares the template's
infrastructure — the Ktor server, Koin, the Postgres toolkit and dev database, fat-jar packaging,
the architecture rules and the agent skills — and replaces the Compose clients with server-rendered
pages. It follows `main` by merging it.

UKPT is a starting point, not a framework. It ships one working feature slice (`:feature:core`) and a
catalog of rules that describe how to grow from it. Copy the slice, follow the rules, delete what you
don't need.

## The stack

| Concern | Choice |
| --- | --- |
| Server | [Ktor](https://ktor.io/) |
| HTML | [kotlinx.html](https://github.com/Kotlin/kotlinx.html), with typed `hx-*`, `sse-*` and Alpine attributes from `dev.isaacudy.udytils:htmx` |
| Interaction | [htmx](https://htmx.org/) 2 and its [`sse` extension](https://htmx.org/extensions/sse/) for live updates; the [Alpine.js CSP build](https://alpinejs.dev/advanced/csp) for state that stays in the browser |
| Styles | Plain CSS on custom-property design tokens; no build step |
| Dependency injection | [Koin](https://insert-koin.io/) |
| Persistence | Postgres, [Exposed](https://github.com/JetBrains/Exposed) and [Flyway](https://flywaydb.org/), with an embedded dev database |
| Architecture rules | [Konsist](https://docs.konsist.lemonappdev.com/), via the udytils architecture system |
| Tests | Ktor `testApplication` with [Jsoup](https://jsoup.org/) selectors; HTML golden snapshots |

## Quick start

```bash
git clone --branch htmx <your-repo> && cd <your-repo>
git submodule update --init --recursive     # required, see Embedded library
./gradlew :app:server:run                   # http://localhost:8080
```

The server starts an embedded Postgres, so there is nothing else to install: no Android SDK and no
Node.js.

To start a real project from the template, use the
[`ukpt-new-project`](.agents/skills/ukpt-new-project) skill. It renames the packages and project
identity, sets up the repository and submodule, and writes the `.ukpt/template.json` marker —
including `"templateBranch": "htmx"` — that later template updates depend on.

## How a page works

The worked example in `:feature:core` is a greetings page:

- **Pages and fragments.** A handler reads from the domain, builds a View State, and renders a Page
  through `ukptLayout`. A request from htmx (`HX-Request: true`) gets the fragment it replaces
  instead of the whole page.
- **Forms work without JavaScript.** Every form has an `action` as well as `hx-post`. An invalid
  submission is re-rendered with its errors under `422`; a valid one answers htmx with a fresh
  form and anything else with a `303` redirect.
- **Live lists over server-sent events.** The list's event stream sends the whole list first, then
  out-of-band appends, updates and deletes (`OobList`), rendered by the same Component the page
  uses. Every open tab stays current.
- **No inline script.** Scripts are files under `static/`, loaded from this origin under a
  `script-src 'self'` content security policy. Alpine components are registered in those files, and
  the markup names them. The rules fail the build on inline script, string `hx-*` attribute names,
  or a CDN URL.

## Architecture

**Read the [architecture README](platform/common/architecture/README.md)** for the project structure
and the rules that govern it. The `server.web` layer is described in
[`docs/serverweb.md`](platform/common/architecture/docs/serverweb.md).

```bash
./gradlew :platform:common:architecture:verifyArchitecture               # run the tests
./gradlew :platform:common:architecture:updateArchitectureDocumentation  # regenerate the documentation
```

## Embedded library

udytils is consumed from source, as a git submodule wired in as a Gradle composite build. It
provides the htmx and HTML snapshot modules, the architecture system and the Postgres toolkit.
`settings.gradle.kts` substitutes every published coordinate for the local project, and the version
catalog declares them with no version, so they can only come from the submodule. Sync it after
pulling:

```bash
git submodule update --init --recursive
```

## Building and testing

```bash
./gradlew test verifyArchitecture validateTemplate     # the whole sweep
./gradlew :feature:core:server:test -PrecordHtmlSnapshots   # re-record HTML goldens
./gradlew :app:server:smokeTestFatJar                  # build and boot the fat jar
```

HTML goldens live in each module's `src/test/snapshots/html/`, normalised so a golden changes only
when the markup does.

## Template updates

- [`.ukpt/template.json`](.ukpt/template.json) records the template branch and version a project is on.
- [`docs/template-migrations/`](docs/template-migrations) documents every change a file sync cannot
  express, with how to detect it and what to do.
- The [`ukpt-template-update`](.agents/skills/ukpt-template-update) skill clones the project's
  template branch and applies the migrations added since the project's template commit.
- [`.ukpt/flavour.json`](.ukpt/flavour.json) lists what this branch dropped from `main` and how
  each divergent file merges. Maintainers merge `main` with the
  [`template-flavour-sync`](.agents/skills/template-flavour-sync) skill.

## Coding agents

The repository works with [Codex](https://developers.openai.com/codex/) and
[Claude Code](https://claude.com/claude-code). [`AGENTS.md`](AGENTS.md) holds project-owned guidance,
[`UKPT.md`](UKPT.md) holds template-owned operational guidance, and [`CLAUDE.md`](CLAUDE.md) imports
both for Claude Code. Skills live in `.agents/skills/`, with links in `.claude/skills/`:

| Skill | Use it to |
| --- | --- |
| [`ukpt-new-project`](.agents/skills/ukpt-new-project) | Turn a fresh copy of the template into a renamed, real project |
| [`ukpt-feature-slice`](.agents/skills/ukpt-feature-slice) | Scaffold `:feature:<name>:{api,server}` with a web layer and wire it up |
| [`ukpt-verify`](.agents/skills/ukpt-verify) | Run the tests, snapshots and checks |
| [`ukpt-run`](.agents/skills/ukpt-run) | Run the server and manage the dev database |
| [`ukpt-architecture-review`](.agents/skills/ukpt-architecture-review) | Review request flows and domain contracts |
| [`ukpt-template-update`](.agents/skills/ukpt-template-update) | Pull the latest template version into a project |

None of this is required. The project is a normal Gradle build.

## License

[Apache 2.0](LICENSE).
