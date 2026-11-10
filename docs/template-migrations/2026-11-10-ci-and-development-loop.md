# CI workflow and development-loop guidance

The template has a CI workflow, `.github/workflows/ci.yml`. It runs the `ukpt-verify` sweep on
every pull request and every push to `htmx`, in one job: the template and architecture rules, the
build-logic tests, every test category including the browser End To End Tests, and the fat jar
smoke test.

`ukpt-testing` has a "Development loop" section: which tests to run while iterating, for each kind
of change, with measured timings; when to check a change by hand, when to write a throwaway End To
End Test, and when to keep one. `UKPT.md`'s Verification section now points there for iteration
and keeps the full sweep for before a pull request.

`UKPT.md` and the skills arrive with the file sync. The workflow is a mixed file: a project keeps
its own jobs and deploy steps.

No rule is added, renamed or removed.

## Detection

```bash
# No CI workflow yet (no output once migrated)
ls .github/workflows/ci.yml 2>/dev/null || echo "no ci.yml"
```

## Migration

1. If the project has no `.github/workflows/ci.yml`, copy the template's, and change the branch
   under `push` to the project's default branch.
2. If it has one, add the template's job beside the project's own, or merge its steps into the
   project's jobs. The job checks out with `submodules: recursive`. GitHub Actions sets `CI`, so
   `installEndToEndBrowser` also installs Chromium's system libraries.

## Verification

Push a branch and open a pull request. The `CI` workflow passes.
