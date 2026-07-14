# The iOS app ships an Xcode project

The template now includes a runnable iOS app at `app/client/ios`, and fixes the iOS entry point,
which was broken.

## What changed

- **New Xcode project** at `app/client/ios/iosApp.xcodeproj`, plus a SwiftUI host (`iOSApp.swift`,
  `ContentView.swift`), an `Info.plist` and an asset catalog. A "Compile Kotlin framework" build phase
  runs `:app:client:common:embedAndSignAppleFrameworkForXcode`, which builds `App.framework` and puts
  it where `FRAMEWORK_SEARCH_PATHS` expects. There is no Gradle command to run the app: open the
  project in Xcode and press ⌘R.
- **`iosMain/MainViewController.kt` now returns `EnroUIViewController { App() }`**, not
  `ComposeUIViewController { App() }`.

## The entry-point bug

This is the part that affects existing projects.

On iOS, `rememberNavigationContainer` resolves its `RootContext` by walking up the parent view
controller hierarchy (`dev.enro.ui.findRootNavigationContext`), and only `EnroUIViewController`
attaches one. Hosting `App()` in a plain `ComposeUIViewController` **compiles**, and every iOS compile
task passes, but the app dies at launch:

```
kotlin.IllegalStateException: Could not find a RootContext in the parent view controller hierarchy
    at dev.enro.ui#findRootNavigationContext
    at dev.enro.ui#rememberNavigationContainer
```

The template shipped this broken because it had no iOS app to run: compiling the iOS targets never
executes the entry point. Any project that copied the template's `MainViewController.kt` has the same
latent bug and has never been able to launch on iOS.

## Detection

- `app/client/ios/` does not exist.
- `iosMain/.../MainViewController.kt` calls `ComposeUIViewController { ... }` rather than
  `EnroUIViewController { ... }`.

## Migration

1. Fix the entry point:
   ```kotlin
   import dev.enro.platform.EnroUIViewController

   fun MainViewController(): UIViewController {
       if (!navigationInstalled) {
           installNavigation()
           navigationInstalled = true
       }
       return EnroUIViewController { App() }   // was ComposeUIViewController
   }
   ```
2. Copy `app/client/ios/` from the template and rename `PRODUCT_BUNDLE_IDENTIFIER` in
   `iosApp.xcodeproj/project.pbxproj` to the project's package. Nothing else in the Xcode project is
   template-branded.
3. Confirm the project's `:app:client:common` declares an iOS framework binary named `App`:
   ```kotlin
   iosTarget.binaries.framework { baseName = "App"; isStatic = true }
   ```
   If it uses a different `baseName`, change `OTHER_LDFLAGS` (`-framework <name>`) to match.

Two settings in the Xcode project are worth knowing about, because both cause obscure failures:

- **`Info.plist` carries `CADisableMinimumFrameDurationOnPhone`.** Compose Multiplatform refuses to
  start without it (`androidx.compose.ui.uikit.PlistSanityCheck`) — the UI would otherwise be capped
  at 60Hz on ProMotion devices. It cannot be supplied via `GENERATE_INFOPLIST_FILE`, which only emits
  keys Xcode knows, so the project uses an explicit `Info.plist` and excludes it from the
  synchronized group's membership (otherwise Xcode reports "Multiple commands produce Info.plist").
- **`EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64`.** `:app:client:common` declares `iosArm64` and
  `iosSimulatorArm64` only. In Release, `ONLY_ACTIVE_ARCH` is `NO`, so Xcode would otherwise request an
  x86_64 simulator slice and Gradle fails with "Xcode Requested Architecture Not Configured". Add an
  `iosX64()` Kotlin target if Intel Macs must run the simulator.

## Verification

```
xcodebuild -project app/client/ios/iosApp.xcodeproj -scheme iosApp \
           -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build
```

Then **launch it in a simulator** — compiling proves nothing here, since the entry-point bug above
compiles cleanly and only fails at runtime. The app should render the `:feature:core` screen.
