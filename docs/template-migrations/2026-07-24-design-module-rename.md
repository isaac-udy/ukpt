# The design system moves to its own module: `:platform:client:design`

The design system — tokens, stateless primitives, and the `design-system/` docs folder — is no
longer housed in `:platform:client:ui`. It now lives in a module named for what it is,
`:platform:client:design` (package root `platform.ui` → `platform.design`).

`:platform:client:ui` is freed up to become what its name implies: a home for **common platform
UI** that legitimately needs navigation or DI — shared scaffolds, nav-aware components — built *on
top of* `:platform:client:design`. The template now ships this as an **empty scaffold**: a
`build.gradle.kts` that depends on `:platform:client:design` (with a commented menu of the Compose,
Enro, and Koin dependencies to enable per component) and a `README.md`, but no components yet. It is
present so the pattern is known and ready; nothing builds from it until a component is added.

`DesignSystemRules.noNavigationOrDi` now scans `/platform/client/design/` instead of
`/platform/client/ui/`, so the "no navigation or DI" boundary follows the design system to its new
module and a future `:platform:client:ui` is free to use both.

## Detection

The project is affected if it has a `:platform:client:ui` module holding the design system (token
files such as `[Prefix]Colors`/`[Prefix]Theme`, primitives, a `design-system/` docs folder). The
package renamed downstream (`platform.ui` becomes the project's own root only if it was renamed at
all — the module path `:platform:client:ui` is not project-specific), so search for the module
directory and its consumers:

```
git grep -l "projects.platform.client.ui"          # build-file consumers
git grep -ln "import platform.ui\."                 # source consumers (adjust if the root was renamed)
```

## Migration

**The clean case — `:platform:client:ui` holds only the design system** (the template's own
starting shape):

1. Rename the module directory `platform/client/ui` → `platform/client/design`, and its package
   directories `…/kotlin/platform/ui` → `…/kotlin/platform/design` (in both `commonMain` and
   `androidHostTest`). Prefer `git mv` so history follows.
2. Rewrite `package platform.ui` → `package platform.design` and every `import platform.ui.…` →
   `import platform.design.…`, and set `namespace = "platform.design"` in the module's
   `build.gradle.kts`.
3. Update `settings.gradle.kts` (`include(":platform:client:ui")` → `":platform:client:design"`)
   and each consumer's `projects.platform.client.ui` → `projects.platform.client.design`.
4. The doc-surface goldens use Paparazzi's flat naming, which encodes the package, so their
   filenames change (`platform.ui_[Class]_[method].png` → `platform.design_…`). Re-record them
   (`./gradlew :platform:client:design:recordPaparazzi --no-configuration-cache`) and update the
   image links in `design-system/**` to match. A package rename does not change pixels, so the
   images are otherwise identical.
5. Add the new empty `:platform:client:ui` scaffold the template now ships — the `build.gradle.kts`
   (depending on `:platform:client:design`) and `README.md` under `platform/client/ui/`, plus its
   `include(":platform:client:ui")` in `settings.gradle.kts`. It builds nothing until a component is
   added, so this is optional, but adopting it keeps the common-UI home a known, ready pattern.

**The mixed case — `:platform:client:ui` has grown beyond the design system.** If the project's
`ui` module already holds *both* the design system *and* other common components — a shared
`AppScaffold`, nav-aware widgets, components that import Enro or Koin — do not move the whole module.
Pick it apart:

1. Move only the design-system parts — the tokens (`[Prefix]Colors`/`Theme`/`Spacing`/…), the
   stateless primitives, and the `design-system/` docs — into the new `:platform:client:design`,
   following the clean-case steps above for just those files.
2. Leave the genuinely-common components in `:platform:client:ui`, and add
   `implementation(projects.platform.client.design)` to that module so they can still read tokens.
3. Point every consumer at whichever module it actually needs: `:platform:client:design` for tokens
   and primitives, `:platform:client:ui` for the shared components. A consumer that used both keeps
   both dependencies.
4. Anything left in `:platform:client:ui` that imports navigation or DI is now allowed there — that
   is the point of the split — but it must **not** appear in `:platform:client:design`, which
   `DesignSystemRules.noNavigationOrDi` still enforces. Moving such a file into `design` by mistake
   fails the architecture suite, which is the intended guard rail.

Three hazards, each hit in a real mixed-case migration:

- **Split packages make prefix rewrites wrong.** When only some symbols of a package move (pure
  primitives go, nav-aware siblings stay), that package ends up split across the two modules. A
  naive `platform.ui.` → `platform.design.` rewrite corrupts the kept files. Build a declaration
  map first — which symbol landed in which module — and rewrite imports symbol-by-symbol against
  it. Files that leaned on same-package visibility to reach a now-moved symbol need new explicit
  imports, so expect the compiler to surface stragglers the map missed.
- **Previews may live in a different module than the composables.** A `PreviewSnapshotTest`
  scanning the design package tree from elsewhere (a feature client module, say) means the preview
  *files* must be split along the same moved/kept line as the composables they render, and their
  directory-grouped goldens `git mv`'d to follow the previews' new declaring packages.
- **The docs corpus may straddle the split.** Pattern pages that document nav-aware components
  staying in `:ui` force a choice when `design-system/` moves into `:design`: cross-module image
  links (docs in `:design` pointing into `:ui`'s snapshot tree) keep one corpus but encode the
  sibling module's directory layout; splitting the corpus keeps links local but scatters the
  documentation. Either is workable — but a project running its own doc-image link checker must
  make sure it resolves whichever form is chosen, from the page's own directory.

## Verification

- `./gradlew :platform:common:architecture:verifyArchitecture` passes — `noNavigationOrDi` finds no
  navigation/DI import under `/platform/client/design/`.
- The client compiles: `./gradlew :app:client:desktop:compileKotlin` (and the other targets).
- `./gradlew :platform:client:design:verifyPaparazzi --no-configuration-cache` passes with the
  re-recorded goldens, and every `design-system/**` image link resolves.
- If a `:platform:client:ui` module remains, its own build and any snapshot goldens pass too.
