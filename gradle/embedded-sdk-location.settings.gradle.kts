// Copies the Android SDK location from this build's local.properties into each embedded build's.
//
// AGP reads `sdk.dir` from the local.properties in the root directory of the build that owns the
// project, falling back to the ANDROID_SDK_ROOT and ANDROID_HOME environment variables. An included
// build is a separate build with its own root directory, so `embedded-enro` and `embedded-udytils`
// never see this project's file. Android Studio writes local.properties for the project it opens
// and exports nothing, so a clone on a machine with no SDK environment variable configures this
// build and then fails to find an SDK inside the submodules.
//
// Values are copied in the escaped form a properties file stores them in, so a Windows `sdk.dir`
// arrives exactly as the IDE wrote it. Both submodules gitignore local.properties.
//
// Applied from settings.gradle.kts rather than living in build-logic: applying a build-logic plugin
// here would put its whole runtime classpath, AGP included, on the settings classpath, and the root
// build could no longer resolve the AGP version it declares.

val sdkDirEntry = Regex("""^[\t ]*sdk\.dir[\t ]*[=:][\t ]*(.*?)[\t ]*\r?$""")

val rootSdkDir = providers
    .fileContents(layout.rootDirectory.file("local.properties"))
    .asText
    .orNull
    ?.lineSequence()
    ?.mapNotNull { sdkDirEntry.matchEntire(it)?.groupValues?.get(1) }
    ?.firstOrNull(String::isNotEmpty)

if (rootSdkDir != null) {
    val embeddedBuilds = layout.rootDirectory.asFile.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("embedded-") && it.resolve("settings.gradle.kts").isFile }

    embeddedBuilds.forEach { embeddedBuild ->
        val localProperties = embeddedBuild.resolve("local.properties")
        val current = if (localProperties.isFile) localProperties.readText() else ""
        val header = when {
            current.isBlank() ->
                "# Written from the root project's local.properties: an included build resolves the\n" +
                    "# Android SDK from its own root directory. Change sdk.dir in the root file, not here.\n"
            else -> ""
        }
        val kept = current.lines().filterNot(sdkDirEntry::matches).dropLastWhile(String::isBlank)
        val updated = (kept + "sdk.dir=$rootSdkDir").joinToString("\n", prefix = header, postfix = "\n")
        if (updated != current) localProperties.writeText(updated)
    }
}
