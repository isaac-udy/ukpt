---
name: ukpt-template-update
description: >-
  Update a project that was created from the ukpt template to the latest template version —
  dependency and submodule bumps, build-logic and skill updates, architecture rule changes, and
  convention migrations. Works from a throwaway clone of the template (no template remote is ever
  added to the project). Use when the user asks to update the project to the latest ukpt.
---

# ukpt-template-update

Update this project to the latest ukpt template. The template lives at
`https://github.com/isaac-udy/ukpt`; this project has no git relationship with it. All comparison
happens against a throwaway clone, and changes land as ordinary commits on an update branch.

Three kinds of change, three mechanisms:

1. **File sync:** template-owned files are three-way merged (`git merge-file`) using the
   throwaway clone for the base and new versions. No shared git history is needed.
2. **Rule sync:** the architecture catalog is merged semantically, never mechanically — projects
   customise their rules, and conflicts go to the user (see §5).
3. **Migrations:** convention changes that only exist in project code are applied by following
   the template's `docs/template-migrations/` entries between the two versions.

## 1. Preconditions

- Clean working tree. Create a branch: `template-update/<new-version>`.
- Read `.ukpt/template.json`. If it does not exist, this is a **first run**: see §8 before
  anything else.
- Clone the template (full history, with submodules) into the scratchpad directory:
  `git clone --recurse-submodules https://github.com/isaac-udy/ukpt <scratchpad>/ukpt-template`.
  Never add it as a remote of this project.
- Resolve the two commits in the clone:
  - **base** = `templateCommit` from the marker (fallback: the commit that last set the marker's
    `templateVersion` in `.ukpt/template.json` — `git log -S`).
  - **new** = the clone's HEAD.
  If base equals new, the project is up to date; stop.

## 2. Self-update first

Sync `.agents/skills/ukpt-*`, the matching `.claude/skills/ukpt-*` compatibility links,
`.claude/settings.json`, and `docs/template-migrations/` from the template clone before anything
else, so the newest update logic runs. The `.claude/skills/` entries must remain relative links to
the canonical `.agents/skills/` directories, not copied skill trees. If this skill's own file
changed, stop and tell the user to re-invoke the skill.

## 3. Read the delta

`git -C <clone> diff --stat <base>..<new>` and read `docs/template-migrations/` entries dated
after the base version. Summarise for the user what the update contains (versions, rules,
migrations, submodule bumps) before changing anything.

## 4. File sync

For each changed template file, translate the template's names through the marker's `project`
rename map (package, name, typePrefix) in both the base and new versions, then three-way merge
into the project:

```
git merge-file <project-file> <translated-base> <translated-new>
```

File classes:

- **Template-owned (expect clean merges):** `UKPT.md`, `gradle/wrapper/`, `build-logic/`,
  `.agents/skills/ukpt-*`, `.claude/settings.json`, `gradle/libs.versions.toml` (projects add
  entries; the merge keeps both), root `build.gradle.kts`, `gradle.properties`. Recreate the
  `.claude/skills/ukpt-*` links exactly; do not pass symlinks to `git merge-file`.
- **Project-owned (never sync):** `AGENTS.md` and `CLAUDE.md`. For projects that predate
  `AGENTS.md`, create it when applying the shared-agent-guidance migration in §6. Otherwise verify
  `AGENTS.md` points agents to `UKPT.md`, and `CLAUDE.md` still imports both files (`@AGENTS.md` and
  `@UKPT.md`).
- **Mixed (expect conflicts; resolve semantically):** `settings.gradle.kts` (project feature
  includes stay), app shells (`app/`), platform modules the project has extended.
- **Example code (do not sync):** `:feature:core` and anything under `feature/` — template
  changes here are patterns, carried by migrations (§6), not file syncs.
- **Identity-bearing (sync structure, never values):** `platform/client/design`. The module's *shape* is
  the template's — which token files exist, the `<Prefix>Theme` wrapper, `DesignSystemDocImagesTest`.
  Everything the project authored *inside* it is that project's identity: the palettes, the type
  scale, the bundled typefaces, the `design-system/` page prose and its prohibition list, every
  primitive beyond the scaffold's, and **every golden** — goldens are renders of the project's
  identity, never the template's, so they are not synced under any circumstances. Apply structural
  template changes; never overwrite authored values or prose. Where a template change touches a file
  the project has authored, present both and ask, exactly as §5 does for the rule catalog. A project
  still on the neutral placeholder palette can take the template side wholesale; a project that runs
  its own design system elsewhere should skip this class entirely.
- **Generated (do not merge by hand):** `platform/common/architecture/README.md` and `docs/` —
  regenerated in §5.

Resolve conflicts by intent: the template side carries infrastructure changes, the project side
carries the project's own content. When a conflict is not clearly one or the other, ask the user.

## 5. Architecture rules — ask, don't overwrite

Projects tweak and extend their rule catalogs. Never treat the catalog as template-owned.

1. Diff the template catalog between base and new:
   `git -C <clone> diff <base>..<new> -- platform/common/architecture/src/main/kotlin/`.
2. For each changed file, compare against the project's version:
   - Project file identical to template base → apply the template change.
   - Project file was customised → present the template change and the project's customisation
     side by side, and **ask the user** how to combine them. Do not guess.
   - Project has added its own rules, Constructs, or RuleGroups → they always survive. If a new
     template rule overlaps or conflicts with a project rule (same concern, different statement,
     colliding IDs), **stop and ask the user** which one wins.
   - Template removed or renamed a rule ID → search the project for references
     (`@ArchitectureException` ruleIds, `// architecture-exception:` comments, prose) and update
     them; migrations usually note these renames.
3. Regenerate and run:
   `./gradlew :platform:common:architecture:updateArchitectureDocumentation` then
   `verifyArchitecture`.
4. New rules failing on existing project code is expected. Present the failures to the user and
   fix the code where the fix is clear. An `@ArchitectureException` requires the user's explicit
   sign-off (`ProjectRules.exceptionsNeedHumanSignOff`) — never add one silently.

## 6. Submodules and migrations

1. Bump `embedded-enro`/`embedded-udytils` to the template's pins at `<new>`
   (`git -C <clone> ls-tree <new> embedded-enro embedded-udytils`), checkout, and record the SHAs
   for the marker. API breaks surface in §7; fix them as part of the update.
2. Apply every `docs/template-migrations/` entry dated after the base version, in order. Each
   entry carries its own detection, steps, and verification. These operate on the project's own
   features — the part no file sync can reach.

## 7. Verify

Run the six-target compile sweep (see the `ukpt-verify` skill), then:

```
./gradlew :platform:common:architecture:verifyArchitecture
./gradlew :feature:<each>:client:verifyPaparazzi --no-configuration-cache
./gradlew validateTemplate
bash .agents/skills/ukpt-verify-web/run-bundle-check.sh
```

Then update `.ukpt/template.json` (`templateVersion`, `templateCommit`, submodule pins), commit,
and summarise the update for the user as PR-ready notes: versions bumped, rules changed,
migrations applied, decisions the user made, exceptions added.

## 8. First run (no `.ukpt/template.json`)

Existing projects predate the marker. Establish it before updating:

1. Ask the user roughly when the project was created from the template (or which template state
   they recognise). Confirm the base commit by fingerprinting: find the template commit that
   minimises the diff against stable template-owned files (`gradle/wrapper/`, `build-logic/`,
   root `build.gradle.kts`). When in doubt, prefer an EARLIER base commit — a too-late base makes
   template changes look like project customisations and silently drops them.
2. Reconstruct the rename map by comparing the project's app shells against the template
   (`com.isaacudy.ukpt` → project package, etc.). Confirm it with the user.
3. Write `.ukpt/template.json` (see `ukpt-new-project` for the shape) with the confirmed base.
4. Proceed from §2. Expect the first update to be large; work through §4–§6 section by section,
   checking in with the user at each phase rather than at the end. Projects that predate the
   architecture registry entirely will effectively adopt it fresh — treat the whole
   `platform/common/architecture/` module as new (copy, then port any pre-existing project rules
   into the catalog with the user's guidance).
