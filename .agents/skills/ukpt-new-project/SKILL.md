---
name: ukpt-new-project
description: >-
  Turn a fresh copy of the ukpt template (zip download or GitHub "use this template") into a real
  project — rename the packages/app identity, set up the git repo and submodules, and write the
  .ukpt/template.json marker that makes future ukpt-template-update runs possible. Use when
  starting a new project from ukpt.
---

# ukpt-new-project

Turn a copy of the ukpt template into a named project. The critical output besides the renames is
`.ukpt/template.json`: it records which template version this project came from and how it was
renamed, which is what `ukpt-template-update` needs later. Do not skip it.

## 1. Establish the baseline BEFORE renaming anything

1. Resolve the template version and commit:
   - `.ukpt/template.json` in the copy has `templateVersion`.
   - Get the matching commit SHA: `git ls-remote https://github.com/isaac-udy/ukpt HEAD` if the
     copy is fresh, or clone the template to a temp dir and find the commit that last set that
     `templateVersion` value (`git log -S '<version>' -- .ukpt/template.json`).
2. Record the submodule pins. A zip download does NOT include submodule content or pins — read
   them from the template clone (`git ls-tree HEAD embedded-enro embedded-udytils`).

## 2. Git + submodules

1. If there is no `.git`, run `git init`.
2. Zip downloads have empty `embedded-enro`/`embedded-udytils` directories. Recreate them as real
   submodules at the recorded pins:
   ```
   git submodule add https://github.com/isaac-udy/Enro embedded-enro
   git submodule add https://github.com/isaac-udy/udytils embedded-udytils
   git -C embedded-enro checkout <pinned-sha>
   git -C embedded-udytils checkout <pinned-sha>
   ```
3. Do NOT add the template repo as a remote. Updates come from a throwaway clone
   (`ukpt-template-update`), not from a persistent remote.

## 3. Rename

Ask the user for the project name and base package if not given. Rename by semantic scope; never
run an unrestricted repository-wide replacement for `ukpt` or `Ukpt`.

Generate the deterministic rename inventory before editing. Read the `REPLACE`, `REVIEW`, and
`KEEP` sections in `build/reports/ukpt/project-rename-plan.txt` and resolve the worked-core-feature
choice in `REVIEW` with the user:

```
./gradlew planProjectRename \
  -Pukpt.newProjectName=<project-name> \
  -Pukpt.newProjectPackage=<package> \
  -Pukpt.newProjectTypePrefix=<ProjectName>
```

| Template value | Becomes | Where |
| --- | --- | --- |
| `com.isaacudy.ukpt` | `<package>` | app shells (packages, directories, `applicationId`), and `PRODUCT_BUNDLE_IDENTIFIER` in `app/client/ios/iosApp.xcodeproj/project.pbxproj` |
| `ukpt` (lowercase word) | `<project-name>` | app/window titles, `rootProject.name`, project-facing README copy |
| `ukpt` (identifier prefix) | lower-camel `<ProjectName>` | code identifiers such as `ukptClientDependencies`; rename only with their declaring feature |
| `Ukpt` type prefix | `<ProjectName>` | `:feature:core` example types, app entry points |
| `feature.ukpt` | `feature.<first-feature>` or leave | `:feature:core` example (see note) |

- Move source directories to match renamed packages.
- Treat the table's `Where` column as an allowlist. Do not rename the UKPT identity in `.agents/`,
  `.claude/`, `.ukpt/`, `UKPT.md`, `docs/template-migrations/`, build convention plugin IDs, or
  version-catalog aliases.
- `:feature:core` is a worked example. Either keep it as-is (recommended until the first real
  feature exists — the architecture examples reference it) or delete it after the first real
  feature is scaffolded with `ukpt-feature-slice`.
- Do not rename anything under `embedded-enro/`, `embedded-udytils/`, or
  `platform/common/architecture/` (the rule catalog's `architecture.rules` package is not
  project-branded).
- `AGENTS.md` is project-owned: rewrite its intro and add any project-specific guidance, but keep
  the instruction to read `UKPT.md`. Keep `CLAUDE.md` as the compatibility file that imports both
  `@AGENTS.md` and `@UKPT.md`. `UKPT.md` is template-owned — leave it alone;
  `ukpt-template-update` syncs it.
- After renaming, search separately for the old package, lowercase name, and type prefix. Review
  every remaining match against the allowlist instead of assuming every match is stale.
- Re-run `planProjectRename` with the same properties plus
  `-Pukpt.renameFailOnReplace=true`. It must report zero `REPLACE` occurrences; `REVIEW` and `KEEP`
  entries may remain by design.

## 4. Write the marker

Write `.ukpt/template.json`:

```json
{
  "templateVersion": "<version from step 1>",
  "templateCommit": "<sha from step 1>",
  "project": {
    "package": "<package>",
    "name": "<project-name>",
    "typePrefix": "<ProjectName>"
  },
  "submodules": {
    "embedded-enro": "<pinned-sha>",
    "embedded-udytils": "<pinned-sha>"
  }
}
```

`project` is the rename map `ukpt-template-update` uses to translate template diffs into this
project's names. Keep it accurate if the project is ever re-branded.

## 5. Verify

Run the full matrix before the first commit:

```
./gradlew :app:client:android:compileDebugKotlin :app:client:desktop:compileKotlin \
          :app:client:web:compileKotlinWasmJs :app:client:common:compileKotlinIosArm64 \
          :app:client:common:compileKotlinIosSimulatorArm64 :app:server:compileKotlin
./gradlew :platform:common:architecture:verifyArchitecture
./gradlew :feature:core:client:verifyPaparazzi --no-configuration-cache
./gradlew validateTemplate
bash .agents/skills/ukpt-verify-web/run-bundle-check.sh

# iOS: the Xcode project must still build after the bundle id is renamed.
xcodebuild -project app/client/ios/iosApp.xcodeproj -scheme iosApp \
           -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build
```

Compiling the iOS targets does not exercise the app — the Compose/Enro entry point only runs when the
app launches. If anything under `iosMain` changed, also run it in a simulator (⌘R from Xcode).

Then commit everything as the project's initial commit.
