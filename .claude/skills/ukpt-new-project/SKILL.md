---
name: ukpt-new-project
description: >-
  Turn a fresh copy of the ukpt template (zip download or GitHub "use this template") into a real
  project — rename the packages/app identity, set up the git repo and submodules, and write the
  .ukpt/template.json marker that makes future ukpt-template-update runs possible. Use when
  starting a new project from ukpt.
argument-hint: <project-name> <package>
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

Ask the user for the project name and base package if not given. Then apply, project-wide:

| Template value | Becomes | Where |
| --- | --- | --- |
| `com.isaacudy.ukpt` | `<package>` | app shells (packages, directories, `applicationId`, bundle ids) |
| `ukpt` (lowercase word) | `<project-name>` | app/window titles, `rootProject.name`, README |
| `Ukpt` type prefix | `<ProjectName>` | `:feature:core` example types, app entry points |
| `feature.ukpt` | `feature.<first-feature>` or leave | `:feature:core` example (see note) |

- Move source directories to match renamed packages.
- `:feature:core` is a worked example. Either keep it as-is (recommended until the first real
  feature exists — the architecture examples reference it) or delete it after the first real
  feature is scaffolded with `ukpt-feature-slice`.
- Do not rename anything under `embedded-enro/`, `embedded-udytils/`, or
  `platform/common/architecture/` (the rule catalog's `architecture.rules` package is not
  project-branded).
- `CLAUDE.md` is project-owned: rewrite its intro for this project, but keep the `@UKPT.md`
  import. `UKPT.md` is template-owned — leave it alone; `ukpt-template-update` syncs it.

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
./gradlew :feature:core:client:verifyPaparazzi
bash .claude/skills/ukpt-verify-web/run-bundle-check.sh
```

Then commit everything as the project's initial commit.
