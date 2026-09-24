# Project agent guidance

Before changing this repository, read [UKPT.md](./UKPT.md) for the template's build, test,
architecture, and submodule guidance. Read the relevant generated architecture documentation under
`platform/common/architecture/` before changing application structure.

This file is project-owned. Put guidance specific to the project here; keep `UKPT.md`
template-owned so template updates can replace it safely. `CLAUDE.md` imports both files for Claude
Code, while Codex discovers this file and the shared skills under `.agents/skills/` directly.

Do not change `embedded-udytils/` unless the task explicitly includes that library. It is an
independent repository mounted as a submodule.
