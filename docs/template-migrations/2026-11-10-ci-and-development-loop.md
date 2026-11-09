# CI workflow and development-loop guidance

The template has a CI workflow, `.github/workflows/ci.yml`. It runs the `ukpt-verify` sweep on
every pull request and every push to `main`, in four parallel jobs: rules and tests, snapshots,
the Android, desktop, web and server builds, and the iOS targets on macOS.

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

1. If the project has no `.github/workflows/ci.yml`, copy the template's.
2. If it has one, add the template's four jobs beside the project's own, or merge their steps into
   the project's jobs. Each job checks out with `submodules: recursive`; the snapshot job needs
   `--no-configuration-cache`.
3. Drop the lines for clients the project deleted: `:app:client:web:wasmJsBrowserDevelopmentWebpack`
   without the canvas web client, `:app:client:web-html:check` without the Compose HTML client.

## Verification

Push a branch and open a pull request. Every job of the `CI` workflow passes.
