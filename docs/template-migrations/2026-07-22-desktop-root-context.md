# Desktop entry point needs an Enro root context

The template's desktop `Main.kt` hosted the root composable in a bare Compose `Window`, which never
registers a `RootContext`. `App`'s `rememberNavigationContainer` requires one, so the desktop
application failed at first composition with `IllegalStateException: No RootContext provided` — the
desktop target could not launch at all. The entry point now opens an Enro `GenericRootWindow` and
renders it with `EnroApplicationContent`.

The other three entry points were always correct and need no change: Android's `ComponentActivity`
is itself a root context, web uses `EnroBrowserContent`, and iOS uses `EnroUIViewController`.

## Detection

A project is affected if its desktop entry point constructs `androidx.compose.ui.window.Window`
directly:

```bash
grep -rn "androidx.compose.ui.window.Window" app/client/desktop/src
```

Any match is affected. Confirm by running `./gradlew :app:client:desktop:run` — an affected project
throws `IllegalStateException: No RootContext provided` at first composition.

## Migration

In `app/client/desktop/src/main/kotlin/**/Main.kt`:

1. Keep the `EnroController` returned by `installNavigationController` instead of discarding it.
2. Register a root window with `controller.openWindow(GenericRootWindow(…))`, moving the root
   composable (`App()`) into the window's content lambda.
3. Replace the `application { Window(…) { … } }` body with `application { EnroApplicationContent(controller) }`.
4. Carry the window title over into `RootWindow.WindowConfiguration`; its default is `"Untitled"`.

Substituting your project's navigation component and title, the result is:

```kotlin
import androidx.compose.ui.window.application
import dev.enro.platform.desktop.GenericRootWindow
import dev.enro.platform.desktop.RootWindow
import dev.enro.platform.desktop.openWindow
import dev.enro.ui.EnroApplicationContent

fun main() {
    val controller = MyProjectNavigation.installNavigationController(Unit)
    controller.openWindow(
        GenericRootWindow(
            windowConfiguration = {
                RootWindow.WindowConfiguration(title = "My Project")
            },
        ) {
            App()
        },
    )
    application {
        EnroApplicationContent(controller)
    }
}
```

Drop the old `onCloseRequest = ::exitApplication`. `WindowConfiguration.onCloseRequest` defaults to
closing the window's navigation handle, which ends the application once the last root window is
gone.

## Verification

```bash
./gradlew :app:client:desktop:run
```

The window must open and render the start destination. Compiling is not sufficient: the entry point
only executes when the application runs, so the missing root context is invisible to
`compileKotlin`. A project that is still broken exits at first composition with
`IllegalStateException: No RootContext provided`.
