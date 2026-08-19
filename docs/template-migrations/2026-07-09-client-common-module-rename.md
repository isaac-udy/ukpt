# Rename `:app:client:shared` to `:app:client:common`

The client's shared KMP library module is renamed from `:app:client:shared` to `:app:client:common`,
so the client group matches the naming already used in the platform layer (`:platform:common:…`) —
"common" everywhere. This is a structural rename: the Gradle module path, its directory, its
type-safe project accessor, and its Android namespace all change. No source packages change (the
module's Kotlin package stays `…ukpt`), and the iOS framework `baseName` stays `App`, so the
consuming Xcode project needs no change.

## Detection

The project is affected if it still has an `:app:client:shared` module — check for the
`app/client/shared/` directory and `include(":app:client:shared")` in `settings.gradle.kts`.

## Migration

1. Rename the directory, preserving history:

   ```
   git mv app/client/shared app/client/common
   ```

2. In `settings.gradle.kts`, change the include:

   ```
   include(":app:client:common")   // was ":app:client:shared"
   ```

3. Update every type-safe project accessor that referenced the old module — in the per-platform app
   modules (`app/client/android`, `app/client/desktop`, `app/client/web`) and any feature/app module
   that depended on it:

   ```
   implementation(projects.app.client.common)   // was projects.app.client.shared
   ```

4. In `app/client/common/build.gradle.kts`, update the Android namespace:

   ```
   namespace = "$projectNamespace.common"   // was "$projectNamespace.shared"
   ```

5. Search the project for any remaining `:app:client:shared` / `app/client/shared` /
   `projects.app.client.shared` references — comments, docs, CI, scripts — and update them. The
   compile command (in the `ukpt-verify` skill) now targets `:app:client:common:compileKotlinIosArm64`
   and `:app:client:common:compileKotlinIosSimulatorArm64`.

## Verification

```
./gradlew :app:client:android:compileDebugKotlin \
          :app:client:desktop:compileKotlin \
          :app:client:web:compileKotlinWasmJs \
          :app:client:common:compileKotlinIosArm64 \
          :app:client:common:compileKotlinIosSimulatorArm64 \
          :app:server:compileKotlin
./gradlew :platform:common:architecture:verifyArchitecture
```

All must be green.
