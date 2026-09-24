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

1. Resolve the template branch, version and commit:
   - `.ukpt/template.json` in the copy has `templateVersion` and `templateBranch` (`main` when
     absent). The branch is the template flavour the project follows from now on.
   - Get the matching commit SHA: `git ls-remote https://github.com/isaac-udy/ukpt refs/heads/<templateBranch>`
     if the copy is fresh, or clone that branch to a temp dir and find the commit that last set that
     `templateVersion` value (`git log -S '<version>' -- .ukpt/template.json`).
2. Record the submodule pins, one per `path` in `.gitmodules`. A zip download does NOT include
   submodule content or pins — read them from the template clone (`git ls-tree HEAD <path>`).

## 2. Git + submodules

1. If there is no `.git`, run `git init`.
2. Zip downloads have empty submodule directories. Recreate each `.gitmodules` entry as a real
   submodule at its recorded pin:
   ```
   git submodule add <url> <path>
   git -C <path> checkout <pinned-sha>
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
| `com.isaacudy.ukpt` | `<package>` | the `:app:server` shell (packages, directories) |
| `ukpt` (lowercase word) | `<project-name>` | page titles, `rootProject.name`, project-facing README copy |
| `ukpt` (identifier prefix) | lower-camel `<ProjectName>` | code identifiers such as `ukptServerDependencies` (rename only with their declaring feature) and `ukptLayout` in `:platform:server:web` (rename with every Page that calls it) |
| `Ukpt` type prefix | `<ProjectName>` | `:feature:core` example types, app entry points |
| `feature.ukpt` | `feature.<first-feature>` or leave | `:feature:core` example (see note) |
| `UKPT_` (env-var prefix) | `<PROJECT_NAME>_`, uppercased with `-` as `_` | `UKPT_DEV_DB*` in `:app:server` **and** in `build-logic/src/main/` |

- Move source directories to match renamed packages.
- Treat the table's `Where` column as an allowlist. Do not rename the UKPT identity in `.agents/`,
  `.claude/`, `.ukpt/`, `UKPT.md`, `docs/template-migrations/`, build convention plugin IDs, or
  version-catalog aliases.
- The all-caps environment-variable prefix is the one exception to `build-logic/` being off-limits:
  the application reads those variables and a convention plugin defaults them, so the two sides
  have to be renamed together. The plan marks them `REPLACE` and says so in the reason.
- `:feature:core` is a worked example. Either keep it as-is (recommended until the first real
  feature exists — the architecture examples reference it) or delete it after the first real
  feature is scaffolded with `ukpt-feature-slice`.
- Do not rename anything under `embedded-udytils/` or
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
  "templateBranch": "<branch from step 1>",
  "templateCommit": "<sha from step 1>",
  "project": {
    "package": "<package>",
    "name": "<project-name>",
    "typePrefix": "<ProjectName>"
  },
  "submodules": {
    "<path>": "<pinned-sha>"
  }
}
```

`project` is the rename map `ukpt-template-update` uses to translate template diffs into this
project's names. Keep it accurate if the project is ever re-branded. `submodules` has one entry
per `.gitmodules` path.

## 5. Verify

Run the `ukpt-verify` sweep before the first commit, then start the server (`ukpt-run`) and load
the page once: the rename touches the layout every page renders through, and the HTML goldens
under `feature/core/server/src/test/snapshots/html/` are re-recorded if the page title changed.

Then commit everything as the project's initial commit.

## 6. Next: the design tokens

The stylesheets in `platform/server/web/src/main/resources/static/platform/css/` carry the
template's **neutral placeholder** tokens — deliberately anonymous, not an identity. Point the user
at `tokens.css` to set the project's palette, type and spacing; `base.css` and every feature
stylesheet read the token names, which stay.
