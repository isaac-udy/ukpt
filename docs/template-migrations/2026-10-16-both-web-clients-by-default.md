# Both web clients build by default

The `ukpt.htmlWebClient` property is removed. `settings.gradle.kts` always includes
`:app:client:web-html`, and `ukpt.html-web` always adds the Kotlin/JS target. A project keeps the
canvas client, the Compose HTML client, or both; `ukpt-new-project` compares them under "Choose the
web clients".

The file sync deletes `build-logic/src/main/kotlin/ukpt/HtmlWebClient.kt`. A build file that still
imports `ukpt.isHtmlWebClientEnabled` fails to compile until step 2 below.

No architecture rule changed.

## Detection

```bash
grep -n 'htmlWebClient' gradle.properties settings.gradle.kts
grep -rln --include='*.gradle.kts' 'isHtmlWebClientEnabled' .
```

## Migration

1. If the project deleted `app/client/web-html`, delete `ukpt.htmlWebClient` and its comment from
   `gradle.properties`. The migration is then done.
2. Otherwise:
   1. Delete `ukpt.htmlWebClient` and its comment from `gradle.properties`.
   2. In `settings.gradle.kts`, replace the `if (providers.gradleProperty("ukpt.htmlWebClient") …)`
      block with `include(":app:client:web-html")`.
   3. In each build file the detection lists, delete `import ukpt.isHtmlWebClientEnabled` and
      replace each `if (isHtmlWebClientEnabled) { … }` block with its contents.
   4. Run `./gradlew :app:client:web-html:compileKotlinJs`. Enro's code generator lists each Compose
      UI screen that has no Compose HTML twin. Write the twins (`ukpt-html-web`, "Add an HTML
      screen"), or remove the client ("Removing the HTML client").

## Verification

```bash
./gradlew verifyArchitecture
./gradlew :app:client:web-html:compileKotlinJs
```

If the project kept the Compose HTML client, also run the "Compose HTML client" section of
`ukpt-verify`.
