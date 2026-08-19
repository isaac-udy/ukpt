---
name: ukpt-ui-atlas
description: >-
  Generating or consulting the UI atlas, orienting in the app's screens and
  navigation before UI work, "what screens are there", "what does X screen look
  like", "what navigates to X".
---

# ukpt-ui-atlas

The UI atlas is a generated map of every screen (Enro `@NavigationDestination`), its Paparazzi
golden variants, and the navigation edges between screens. It also synthesizes gallery cards for
preview goldens that have no matching destination.

## Generating

```
./gradlew generateUiAtlas
```

Output lands in `build/ui-atlas/`:
- `index.html` — interactive atlas (open in a browser to pan, zoom, search, and browse variants).
- `manifest.json` — machine-readable UI map.
- `images/` — copied golden PNGs.

Goldens must exist before generating. If screens are missing images, run `recordPaparazzi` for the
relevant client modules first (see the `ukpt-verify` skill).

## Agent orientation via `manifest.json`

Before starting UI work, read `build/ui-atlas/manifest.json` (generate it first if it does not
exist) instead of grepping for destinations. It answers what screens exist, what opens what, and
which goldens show a screen's states.

### Manifest fields

**`AtlasManifest`** (root):
- `projectName` — the project name.
- `generatedAt` — ISO 8601 timestamp.
- `nodes` — all destination nodes (real and synthetic).
- `edges` — resolved navigation edges.
- `unresolvedEdges` — edges the scanner found in source but could not resolve to a known node.
- `unmatchedGoldens` — golden files that matched no destination.
- `totalGoldens` — total golden PNG files discovered.

**`AtlasNode`**:
- `qualifiedName` — fully qualified destination name (`package.DestName`).
- `destinationName` — simple destination class name.
- `screenName` — destination name without the `Destination` suffix.
- `displayName` — human-readable name (disambiguated when duplicates exist).
- `featureGroup` — feature grouping label.
- `moduleLabel` — Gradle module path derived from file location.
- `sourceFile` — repo-relative path to the declaring source file.
- `packageName` — Kotlin package name.
- `isWithResult` — `true` if the destination key extends `NavigationKey.WithResult`.
- `navigationPath` — value from `@NavigationPath`, if present.
- `isShellActive` — `true` if the screen uses `shellActive()` or `shellEmpty()`.
- `variants` — matched snapshot variants, each with `label`, `imagePath`, `width`, `height`.
- `defaultVariantIndex` — index of the default variant to display.
- `synthetic` — `true` if this node was synthesized from orphan goldens.

**`AtlasEdge`**: `source` and `target` are `qualifiedName` values; `kind` is `OPEN`, `RESULT`,
or `CHROME`.

**`UnresolvedEdge`**: `file` (repo-relative path), `line`, `text` (the source line).

## Caveats

- Edge scanning is regex-heuristic: it finds `.open(` calls and maps them to known destinations.
  It does not evaluate code, so edges inside conditionals or generated code may be missed or
  spurious.
- Unresolved edges are listed in the manifest (`unresolvedEdges`), not dropped.
- The atlas auto-discovers source and golden roots by walking the repo and pruning `.gitmodules`
  submodules, `build/` directories, and dot-dirs.

## Reference
- Plugin source: [`embedded-udytils/atlas/`](../../../embedded-udytils/atlas/README.md).
- Model types: [`embedded-udytils/atlas/core/src/main/kotlin/dev/isaacudy/udytils/atlas/AtlasModel.kt`](../../../embedded-udytils/atlas/core/src/main/kotlin/dev/isaacudy/udytils/atlas/AtlasModel.kt).
