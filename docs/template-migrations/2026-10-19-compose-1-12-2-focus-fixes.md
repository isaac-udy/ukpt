# Compose Multiplatform 1.12.2 text-field focus fixes

The template now uses Compose Multiplatform 1.12.2. The catalog bump arrives with the file sync. It
fixes two text-field focus bugs that a project may have worked around:

- **iOS 27 Safari, canvas web client** (CMP-10825, compose-multiplatform-core #3466): a
  `TextField` takes focus but the virtual keyboard does not open.
- **Native iOS** (compose-multiplatform-core #3459): the app crashes when a focused `TextField`
  loses focus because a `Dialog` opens.

A workaround for either bug is now dead code. This migration finds candidates for review; it
changes no template convention.

Skip the first bug if the project deleted `app/client/web`. Skip the second if it has no iOS app.

## Detection

```bash
grep -rnE 'clearFocus\(|userAgent|iPhone|iPad|Safari|\.focus\(\)|inputmode' \
  --include='*.kt' --include='*.html' --include='*.js' \
  app feature platform | grep -v '/build/'
```

Each hit is a candidate, not a confirmed workaround. Typical shapes:

- iOS Safari: a user-agent check for iPhone, iPad or Safari; JavaScript that focuses a hidden
  `<input>` or the canvas; an `inputmode` attribute set from `wasmJsMain` or `index.html`.
- Native iOS: `LocalFocusManager.current.clearFocus()` or `FocusRequester.freeFocus()` called
  before a dialog destination opens; a delay between clearing focus and opening the dialog.

## Migration

1. For each hit, read the code and its commit history. Decide whether it exists only for one of the
   two bugs above.
2. Remove each workaround that exists only for one of these bugs, with its comments and any
   iOS-only `expect`/`actual` it used.
3. Keep focus handling that has another purpose, such as dismissing the keyboard when a form is
   submitted.

## Verification

```bash
./gradlew :app:client:web:wasmJsBrowserDevelopmentWebpack
./gradlew :app:client:common:compileKotlinIosSimulatorArm64
```

Then, for each removed workaround, repeat the original reproduction: tap a text field in the canvas
web client in Safari on an iOS 27 device, and open a dialog from a screen with a focused text field
in the iOS app. If the project has no iOS 27 device, record the removal as unverified in the pull
request.
