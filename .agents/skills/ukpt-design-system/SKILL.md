---
name: ukpt-design-system
description: >-
  Establish or extend a UKPT project's design system in :platform:client:design —
  author the identity (palette, type scale, typefaces, principles,
  prohibitions) over the template's neutral scaffold, choose what primitives
  are built on, and add new primitives together with their doc page and
  doc-surface golden. Use when setting up a project's design system, changing
  its tokens, or adding a design-system component.
---

# ukpt-design-system

The template ships `:platform:client:design` as a **working but anonymous** system: neutral greyscale
palette, system typefaces, one primitive. This skill turns it into a project's own, and adds
primitives to it afterwards.

## Orient first — read the project's actual design module

Everything below describes the **scaffold** the template ships: a module at `:platform:client:design`
(package `platform.design`), `<Prefix>Theme`/`<Prefix>Colors` tokens, a `design-system/` docs folder,
and Paparazzi doc-surface goldens. A project that authored its own design system — a path the
2026-07-22.3 migration explicitly endorses — may match none of it. Read the actual module first and
treat the scaffold shapes as one possible answer, not the contract:

- **Find the design module** (it may not be `:platform:client:design`) and read its sources. Note the
  **theme wrapper's signature** — the scaffold's `<Prefix>Theme(colors, content)` may instead be a
  `GtTheme(density, content)` with no `colors` parameter — the **token accessor** (`<Prefix>Theme.colors`
  vs. a bare `Gt.colors` object), and the **type prefix**. Use what you find, not the names below.
- **Check whether the doc pipeline exists**: a `design-system/` folder and Paparazzi on the module. If
  it does, follow the doc-page / doc-surface / `recordPaparazzi` steps verbatim. If it does not, the
  token-authoring steps still apply to whichever files hold the tokens, but there are no doc pages or
  doc-surface goldens to maintain — skip those steps, or offer to set the pipeline up.

On the scaffold (the common case, and any project from `ukpt-new-project`) read
[`platform/client/design/design-system/README.md`](../../../platform/client/design/design-system/README.md)
and `principles.md` first — they are the contract this skill maintains, not background reading.

Two modes. Pick from what the user asked for; if it is ambiguous, ask.

---

# Mode 1 — Establish the identity (once per project)

## Step 0 — Ask before assuming

**Ask the user two things before touching anything.** Do not go hunting through the repo for a
design document, and do not assume a conventional location:

1. *"Do you have a design handoff or spec I can base this on — and if so, where is it? Or would you
   rather work through it together?"* A handoff can be anything: a Markdown spec, a Figma export, a
   DESIGN.md, a folder of notes. Read whatever they point at.
2. *"What should the primitives be built on?"* — see Step 1.

If there is no handoff, work through the identity **with** the user rather than inventing one. Do
not silently pick a palette; an identity nobody chose is worse than the neutral placeholder, because
it looks decided.

## Step 1 — Choose what primitives are built on

This is the decision that is expensive to reverse, so make it explicitly and record it. Present the
trade-off (also in `design-system/README.md` → "What to build primitives on"):

| Basis | Gets you | Costs you |
|---|---|---|
| **Material3** | Semantics, minimum touch targets, state layers, ripple, focus, a11y — free | Material's structural opinions; stubborn when the identity diverges |
| **Compose Unstyled** | Behaviourally complete, visually neutral primitives | An extra dependency; smaller component set |
| **From scratch** (scaffold default) | Total control | You re-implement interaction and accessibility, and omissions are invisible to visual review |

Then **adapt the scaffold to the answer** — do not leave a basis the project didn't choose:

- **Material3** — rewrite the shipped primitive to wrap `androidx.compose.material3.Button`, styling
  it via `ButtonDefaults.buttonColors(...)`, `shape` and `contentPadding` from tokens
  (templates.md §1). Promote `material3` from `implementation` to `api` in
  `platform/client/design/build.gradle.kts`, since material types now appear in the primitive's surface.
  This composes cleanly: `<Prefix>Theme` already wraps a `MaterialTheme` derived from the tokens, so
  a material component inside it inherits the palette and type scale with no extra wiring.
- **Compose Unstyled** — add the dependency to `gradle/libs.versions.toml` and the module, then
  rebuild the primitive on it. **Check the library's current API rather than trusting a template
  here** — this skill deliberately does not carry a verbatim Compose Unstyled snippet, because a
  stale one would be worse than none. Keep the contract in templates.md §3 (stateless, variant enum,
  tokens only, explicit semantics) whatever the API turns out to be.
- **From scratch** — keep what ships, and tell the user plainly what they are taking on: the current
  button has no minimum touch target, no focus indication and no state layers.

Record the decision in `design-system/README.md` so the next person doesn't relitigate it.

## Step 2 — Confirm the type prefix

Design-system types carry the **project's** prefix (`<Prefix>Theme`, `<Prefix>Colors`). Read
`platform/client/design/src/commonMain/kotlin/platform/design/` to see what it currently is.

For a project created by `ukpt-new-project`, the rename already happened — `Ukpt` is the project type
prefix `ProjectRenamePlanner` rewrites, and `platform/client/design` is not a protected path, so its
sources *and* its `design-system/` pages were renamed with everything else. Usually there is nothing
to do here.

If the module still says `Ukpt` in a project that isn't called ukpt (it was adopted later via a
template update), rename `Ukpt*` → `<Prefix>*` across the module, including the docs pages, before
going further.

## Step 3 — Author the tokens

Edit in place; the files are one-per-concern and already documented. Take values from the handoff
where there is one, and from the user where there isn't.

- `<Prefix>Colors.kt` — replace the `Light`/`Dark` placeholder palettes. Keep the roles semantic; if
  the handoff names hues ("brand blue"), map them onto roles rather than renaming roles after hues.
  Add a role rather than allowing a literal, and mark any value the spec never defined as a non-spec
  addition naming whose judgement it is.
- `<Prefix>Fonts.kt` — bundle variable TTFs under `src/commonMain/composeResources/font/` (lowercase
  filenames) with the licence and source noted alongside them, and load explicit weights from the
  variable file. Bundled fonts **do** render under Paparazzi, so doc surfaces show the real typeface;
  do not add a "use system fonts in tests" workaround.
- `<Prefix>Typography.kt` — keep `from(fonts)`; change sizes/weights, not the shape.
- `<Prefix>Spacing.kt` / `<Prefix>Shapes.kt` — adjust the scales. Keep them bare objects: spacing is
  one density on purpose.
- `<Prefix>Viewport.kt` — set `Default` to the project's primary form factor, and keep
  `<Prefix>PreviewFrame.kt`'s default height describing the same device — the frame is what sizes
  every screen preview's golden.

## Step 4 — Author the principles and prohibitions

`design-system/principles.md` ships the generalisable principles worded for the template. Reword them
in the project's terms and, most importantly, **fill in the prohibition list** with the decisions this
project has actually made. The list earns its keep by recording the argument that was had — a generic
list is ignored.

## Step 5 — Re-record and verify

Scaffold doc pipeline only (see "Orient first"): a module without Paparazzi has no goldens to
re-record — verify the identity by running the app and looking at it instead.

```
./gradlew :platform:client:design:recordPaparazzi --no-configuration-cache --max-workers=2
./gradlew :platform:client:design:verifyPaparazzi --no-configuration-cache --max-workers=2
```

Then **look at the goldens** — a green snapshot run only proves determinism, not that the identity
renders as intended. Feature goldens change too, since screens render on the new palette:

```
./gradlew :feature:<name>:client:recordPaparazzi --no-configuration-cache --max-workers=2
```

Review those diffs rather than accepting them wholesale.

---

# Mode 2 — Add a primitive (recurring)

## Step 0 — Push back first

A new primitive is a discussion, not a commit. Ask: **which existing primitive should have absorbed
this, and why can't it?** Most requests are a new *variant* on an existing primitive — add an entry to
its variant enum instead, which is one edit and no new doc page.

Proceed only if the answer is genuinely "none of them".

## Step 1 — The three files land together

This is the rule the whole docs pipeline rests on. A primitive without its doc page and doc-surface
test is how a system starts drifting from its documentation.

This assumes the scaffold's doc pipeline (a `design-system/` folder + Paparazzi on the module — see
"Orient first"). If the project doesn't have it, only file 1 (the component) applies; files 2–3 (the
doc surface and doc page) exist only where that pipeline does — offer to set it up, or skip them.

1. **Component** — `src/commonMain/kotlin/platform/design/components/<Prefix><Name>.kt`.
   Stateless (props in, events out; no `remember`-ed state, no `rememberSomething()` API), variant
   `enum class` in the same file, every value a token, explicit `role`/semantics on anything
   interactive, and a non-interactive state that keeps its meaning in `stateDescription` rather than
   in colour alone. Match whatever basis Step 1 of Mode 1 chose (templates.md §1–3).
2. **Doc surface** — `@Preview` functions in `src/androidHostTest/kotlin/platform/design/`, each
   wrapping a curated sheet in `DocSurface(colors, width, height)` (templates.md §5): every variant,
   labelled, one preview per palette with the palette passed explicitly (not `uiMode` qualifiers —
   the harness doesn't apply `uiMode`, and explicit functions keep golden names clean). The sheets
   are compositions *about* the system, not copies of app screens, and they stay in the test source
   set so they never ship in the artifact. The module's `PreviewSnapshotTest` discovers them and
   renders in `RenderingMode.SHRINK`, cropping each golden to its `DocSurface` container.
3. **Doc page** — `design-system/components/<name>.md` (templates.md §4), embedding the golden and
   ending in a **Rules** section. A page with nothing to forbid is describing an implementation
   detail, not a design decision. Add it to the table in `design-system/README.md`.

Goldens are **directory-grouped** by the preview's declaring package and function name, so the page
embeds `../../src/androidHostTest/snapshots/images/platform/design/<PreviewName>.png`. **Preview
function names are load-bearing**: renaming one renames its golden and breaks the embed.

## Step 2 — Record and verify

```
./gradlew :platform:client:design:recordPaparazzi --no-configuration-cache --max-workers=2
./gradlew :platform:client:design:verifyPaparazzi --no-configuration-cache --max-workers=2
```

`verifyPaparazzi` also runs `DesignSystemDocImagesTest`, which fails if the page links to an image
that doesn't exist — so a filename typo or a renamed method is caught here rather than months later.
**Open the golden and look at it** before calling this done.

---

## Gotchas

- **`--no-configuration-cache` on every Paparazzi task.** Paparazzi's resource-preparation task can't
  be stored in the configuration cache.
- **Composables under test must be at least `internal`**, not `private`, or the host-test source set
  can't reach them.
- **Don't add navigation or DI to this module.** `DesignSystemRules.noNavigationOrDi` enforces it. A
  primitive that seems to need navigation is missing a callback parameter — the caller navigates.
- **Don't put non-design-system client infrastructure here** either (markdown rendering, pickers,
  error-reporter seams). Those are sibling `:platform:client:*` modules; the design system stays a
  pure Compose module.
- **Changing a token re-records every golden**, in this module and in every feature. That is the
  system working, but review the diffs.
- **This is a template-owned module.** Changing its *structure* (not its values) is a template change:
  see UKPT.md → Template versioning.
